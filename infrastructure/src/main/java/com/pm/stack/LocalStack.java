package com.pm.stack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import software.amazon.awscdk.App;
import software.amazon.awscdk.AppProps;
import software.amazon.awscdk.BootstraplessSynthesizer;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.Token;
import software.amazon.awscdk.services.ec2.ISubnet;
import software.amazon.awscdk.services.ec2.InstanceClass;
import software.amazon.awscdk.services.ec2.InstanceSize;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.amazon.awscdk.services.elasticache.CfnCacheCluster;
import software.amazon.awscdk.services.elasticache.CfnSubnetGroup;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.amazon.awscdk.services.msk.CfnCluster;
import software.amazon.awscdk.services.rds.Credentials;
import software.amazon.awscdk.services.rds.DatabaseInstance;
import software.amazon.awscdk.services.rds.DatabaseInstanceEngine;
import software.amazon.awscdk.services.rds.PostgresEngineVersion;
import software.amazon.awscdk.services.rds.PostgresInstanceEngineProps;
import software.amazon.awscdk.services.route53.CfnHealthCheck;
import software.amazon.awscdk.services.servicediscovery.DnsRecordType;


/**
 * LocalStack
 * ----------
 * CDK stack responsible for provisioning the complete local infrastructure
 * required for the Patient Management system.

 * This includes:
 *  - VPC & networking
 *  - ECS Cluster (Fargate)
 *  - RDS PostgreSQL databases
 *  - MSK (Kafka) cluster
 *  - Microservices as Fargate services
 *  - API Gateway service exposed via ALB

 * Designed primarily for local / integration environments using LocalStack.
 */
public class LocalStack extends Stack {

    /** Shared VPC for all infrastructure components */
    private final Vpc vpc;

    /** ECS Cluster hosting all microservices */
    private final Cluster ecsCluster;

    /** Redis / ElastiCache cluster shared across microservices.
    This cluster is created once and injected into services via environment variables **/
    private final CfnCacheCluster elastiCacheCluster;

    public LocalStack(final App scope, final String id, final StackProps props) {
        super(scope, id, props);

        /* =========================
           Networking
           ========================= */
        this.vpc = createVpc();

        /* =========================
           Databases (RDS PostgreSQL)
           ========================= */
        DatabaseInstance authServiceDb =
                createDatabase("AuthServiceDB", "auth-service-db");

        DatabaseInstance patientServiceDb =
                createDatabase("PatientServiceDB", "patient-service-db");

        /* =========================
           Database Health Checks
           Used to ensure DB availability
           before dependent services start
           ========================= */
        CfnHealthCheck authDbHealthCheck =
                createDbHealthCheck(authServiceDb, "AuthServiceDBHealthCheck");

        CfnHealthCheck patientDbHealthCheck =
                createDbHealthCheck(patientServiceDb, "PatientServiceDBHealthCheck");

        /* =========================
           Kafka (MSK)
           ========================= */
        CfnCluster mskCluster = createMskCluster();

        /* =========================
           ECS Cluster
           ========================= */
        this.ecsCluster = createEcsCluster();

        // Create the Redis cluster during stack initialization
        this.elastiCacheCluster = createRedisCluster();

        /* =========================
           Auth Service
           ========================= */
        FargateService authService =
                createFargateService(
                        "AuthService",
                        "auth-service",
                        List.of(4005),
                        authServiceDb,
                        Map.of("JWT_SECRET", "8rDa8aJyAW6Bbh4f3YqkRPI8yf3gN4uVR06mGv44pww=")
                );

        // Ensure DB and health check exist before service starts
        authService.getNode().addDependency(authDbHealthCheck);
        authService.getNode().addDependency(authServiceDb);

        /* =========================
           Billing Service
           ========================= */
        FargateService billingService =
                createFargateService(
                        "BillingService",
                        "billing-service",
                        List.of(4001, 9001),
                        null,
                        null
                );

        /* =========================
           Analytics Service
           Depends on Kafka
           ========================= */
        FargateService analyticsService =
                createFargateService(
                        "AnalyticsService",
                        "analytics-service",
                        List.of(4002),
                        null,
                        null
                );

        analyticsService.getNode().addDependency(mskCluster);

        /* =========================
           Patient Service
           Depends on:
           - Patient DB
           - Billing Service
           - Kafka
           ========================= */
        FargateService patientService =
                createFargateService(
                        "PatientService",
                        "patient-service",
                        List.of(4000),
                        patientServiceDb,
                        Map.of(
                               // "BILLING_SERVICE_ADDRESS", "host.docker.internal",  ---this is okay for development and both docker and localstack because both of them system are using docker networking but whenever we deploy our stack to the real aws on production it will break because ecs does not use docker DNS networking instead it uses it own things, and its better to use the ecs cloud map service discovery functionality
                                    "BILLING_SERVICE_ADDRESS", "billing-service.patient-management.local",
                                "BILLING_SERVICE_GRPC_PORT", "9001"
                        )
                );

        /* Ensure Patient Service is deployed only after all this thing are available
            Prevents startup issues caused by missing things Redis endpoint **/
        patientService.getNode().addDependency(patientServiceDb);
        patientService.getNode().addDependency(patientDbHealthCheck);
        patientService.getNode().addDependency(billingService);
        patientService.getNode().addDependency(mskCluster);

        /* Ensure Patient Service is deployed only after Redis is available
         Prevents startup issues caused by Redis endpoint **/
        patientService.getNode().addDependency(elastiCacheCluster);

        /* =========================
           API Gateway
           Public entry point
           ========================= */
        ApplicationLoadBalancedFargateService apiGateway = createApiGatewayService();

        /* API Gateway also depends on Redis because it uses rate limiting
         CDK will deploy Redis first before starting the service */
        apiGateway.getNode().addDependency(elastiCacheCluster);


        // Prometheus monitoring service for collecting application metrics
        // Runs as a standalone ECS Fargate service inside the cluster

        // Purpose:
        // - Scrapes metrics exposed by microservices
        // - Stores time-series monitoring data
        // - Used later for Grafana dashboards and observability

        // Current setup:
        // - Scrapes Patient Service actuator metrics
        // - Uses internal Cloud Map DNS for service discovery

        // Port:
        // - 9090 -> Default Prometheus UI and API port

        // NOTE:
        // - Service name "prometheus-prod" maps to a custom Docker image
        //   containing production Prometheus configuration.
        FargateService prometheusService = createFargateService(
                "PrometheusService",
                "prometheus-prod",
                List.of(9090),
                null,
                null
        );

        createGrafanaService();
    }


    /**
     * Creates a VPC with private subnets across 2 AZs.
     * Used by ECS, RDS, and MSK.
     */
    private Vpc createVpc() {
        return Vpc.Builder
                .create(this, "PatientManagementVPC")
                .vpcName("PatientManagementVPC")
                .maxAzs(2)
                .build();
    }

    /**
     * Creates a PostgreSQL RDS instance.
     *
     * @param id     CDK logical ID
     * @param dbName Database name
     */
    private DatabaseInstance createDatabase(String id, String dbName) {
        return DatabaseInstance.Builder
                .create(this, id)
                .engine(DatabaseInstanceEngine.postgres(
                        PostgresInstanceEngineProps.builder()
                                .version(PostgresEngineVersion.VER_17_2)
                                .build()))
                .vpc(vpc)
                .instanceType(InstanceType.of(InstanceClass.BURSTABLE2, InstanceSize.MICRO))
                .allocatedStorage(20)
                .credentials(Credentials.fromGeneratedSecret("postgres"))
                .databaseName(dbName)
                .removalPolicy(RemovalPolicy.DESTROY) // Local/dev only
                .build();
    }

    /**
     * Creates a Route53 TCP health check for an RDS instance.
     * Used to control startup ordering of ECS services.
     */
    private CfnHealthCheck createDbHealthCheck(DatabaseInstance db, String id) {
        return CfnHealthCheck.Builder.create(this, id)
                .healthCheckConfig(CfnHealthCheck.HealthCheckConfigProperty.builder()
                        .type("TCP")
                        .port(Token.asNumber(db.getDbInstanceEndpointPort()))
                        .ipAddress(db.getDbInstanceEndpointAddress())
                        .requestInterval(30)
                        .failureThreshold(3)
                        .build())
                .build();
    }

    /**
     * Creates an MSK (Kafka) cluster used by analytics and event-driven services.
     */
    private CfnCluster createMskCluster() {
        return CfnCluster.Builder.create(this, "MskCluster")
                .clusterName("kafa-cluster")
                .kafkaVersion("3.4.0")
                .numberOfBrokerNodes(2)
                .brokerNodeGroupInfo(CfnCluster.BrokerNodeGroupInfoProperty.builder()
                        .instanceType("kafka.m5.xlarge")
                        .clientSubnets(
                                vpc.getPrivateSubnets().stream()
                                        .map(ISubnet::getSubnetId)
                                        .collect(Collectors.toList())
                        )
                        .brokerAzDistribution("DEFAULT")
                        .build())
                .build();
    }

    /**
     * Creates the ECS cluster with Cloud Map service discovery enabled.
     */
    private Cluster createEcsCluster() {
        return Cluster.Builder.create(this, "PatientManagementCluster")
                .vpc(vpc)
                .defaultCloudMapNamespace(CloudMapNamespaceOptions.builder()
                        .name("patient-management.local")
                        .build())
                .build();
    }

    /**
     * Generic factory method for creating Fargate services.

     * Handles:
     *  - Task definition
     *  - Logging
     *  - Port mappings
     *  - Kafka + DB environment variables
     */
    private FargateService createFargateService(
            String id,
            String imageName,
            List<Integer> ports,
            DatabaseInstance db,
            Map<String, String> additionalEnvVars) {

        FargateTaskDefinition taskDefinition =
                FargateTaskDefinition.Builder.create(this, id + "Task")
                        .cpu(256)
                        .memoryLimitMiB(512)
                        .build();

        ContainerDefinitionOptions.Builder containerOptions =
                ContainerDefinitionOptions.builder()
                        .image(ContainerImage.fromRegistry(imageName))
                        .portMappings(
                                ports.stream()
                                        .map(port -> PortMapping.builder()
                                                .containerPort(port)
                                                .hostPort(port)
                                                .protocol(Protocol.TCP)
                                                .build())
                                        .toList()
                        )
                        .logging(LogDriver.awsLogs(
                                AwsLogDriverProps.builder()
                                        .logGroup(LogGroup.Builder.create(this, id + "LogGroup")
                                                .logGroupName("/ecs/" + imageName)
                                                .removalPolicy(RemovalPolicy.DESTROY)
                                                .retention(RetentionDays.ONE_DAY)
                                                .build())
                                        .streamPrefix(imageName)
                                        .build()
                        ));

        Map<String, String> envVars = new HashMap<>();

        // Kafka bootstrap servers (LocalStack)
        envVars.put(
                "SPRING_KAFKA_BOOTSTRAP_SERVERS",
                "localhost.localstack.cloud:4510, localhost.localstack.cloud:4511, localhost.localstack.cloud:4512"
        );

        /* Spring Boot Redis configuration.
           Inject Redis endpoint dynamically from ElastiCache **/
        envVars.put("SPRING_CACHE_TYPE", "redis");
        envVars.put("SPRING_DATA_REDIS_HOST", elastiCacheCluster.getAttrRedisEndpointAddress());
        envVars.put("SPRING_DATA_REDIS_PORT", elastiCacheCluster.getAttrRedisEndpointPort());

        if (additionalEnvVars != null) {
            envVars.putAll(additionalEnvVars);
        }

        if (db != null) {
            envVars.put(
                    "SPRING_DATASOURCE_URL",
                    "jdbc:postgresql://%s:%s/%s-db".formatted(
                            db.getDbInstanceEndpointAddress(),
                            db.getDbInstanceEndpointPort(),
                            imageName
                    )
            );
            envVars.put("SPRING_DATASOURCE_USERNAME", "postgres");
            envVars.put(
                    "SPRING_DATASOURCE_PASSWORD",
                    db.getSecret().secretValueFromJson("password").toString()
            );
            envVars.put("SPRING_JPA_HIBERNATE_DDL_AUTO", "update");
            envVars.put("SPRING_SQL_INIT_MODE", "always");
            envVars.put("SPRING_DATASOURCE_HIKARI_INITIALIZATION_FAIL_TIMEOUT", "60000");
        }

        containerOptions.environment(envVars);
        taskDefinition.addContainer(imageName + "Container", containerOptions.build());

        return FargateService.Builder.create(this, id)
                .cluster(ecsCluster)
                .taskDefinition(taskDefinition)
                .assignPublicIp(false)
                .cloudMapOptions(CloudMapOptions.builder()
                        .name(imageName) //patient-service.patient-management.local //-> patient-service--- this is the name of the service //-> patient-management.local---- this is the name of the namespace
                        .dnsRecordType(DnsRecordType.A)
                        .build())
                .serviceName(imageName)
                .build();
    }

    /**
     * Creates the API Gateway service exposed via an Application Load Balancer.
     */
    private ApplicationLoadBalancedFargateService createApiGatewayService() {

        FargateTaskDefinition taskDefinition =
                FargateTaskDefinition.Builder.create(this, "APIGatewayTaskDefinition")
                        .cpu(256)
                        .memoryLimitMiB(512)
                        .build();

        ContainerDefinitionOptions containerOptions =
                ContainerDefinitionOptions.builder()
                        .image(ContainerImage.fromRegistry("api-gateway"))
                        .environment(Map.of(
                                "SPRING_PROFILES_ACTIVE", "prod",
                                //"AUTH_SERVICE_URL", "http://host.docker.internal:4005" // in place of development we are going to reference the Auth service with cloud map name
                                "AUTH_SERVICE_URL", "http://auth-service.patient-management.loacal:4005",

                                /* Container environment variables for API services
                                   Redis endpoint is resolved automatically after cluster creation */
                                "REDIS_HOST", elastiCacheCluster.getAttrRedisEndpointAddress(),
                                "REDIS_PORT", elastiCacheCluster.getAttrRedisEndpointPort()
                        ))
                        .portMappings(
                                List.of(4004).stream()
                                        .map(port -> PortMapping.builder()
                                                .containerPort(port)
                                                .hostPort(port)
                                                .protocol(Protocol.TCP)
                                                .build())
                                        .toList()
                        )
                        .logging(LogDriver.awsLogs(
                                AwsLogDriverProps.builder()
                                        .logGroup(LogGroup.Builder.create(this, "ApiGatewayLogGroup")
                                                .logGroupName("/ecs/api-gateway")
                                                .removalPolicy(RemovalPolicy.DESTROY)
                                                .retention(RetentionDays.ONE_DAY)
                                                .build())
                                        .streamPrefix("api-gateway")
                                        .build()
                        ))
                        .build();

        taskDefinition.addContainer("APIGatewayContainer", containerOptions);
        ApplicationLoadBalancedFargateService apiGateway =
                ApplicationLoadBalancedFargateService.Builder.create(this, "APIGatewayService")
                .cluster(ecsCluster)
                .serviceName("api-gateway")
                .taskDefinition(taskDefinition)
                .desiredCount(1)
                .healthCheckGracePeriod(Duration.seconds(60))
                .publicLoadBalancer(true)
                .cloudMapOptions(CloudMapOptions.builder()
                        .name("api-gateway")
                        .dnsRecordType(DnsRecordType.A)
                        .build())
                .build();

        return apiGateway;
    }

    /**
     * Creates a Redis ElastiCache cluster inside private subnets.

     * Why private subnets?
     * - Redis should not be publicly accessible.
     * - Only ECS services inside the VPC can communicate with it.

     * Security:
     * - Uses VPC default security group for internal communication.

     * Current setup:
     * - Single-node Redis cluster
     * - Suitable for development/testing workloads

     * NOTE:
     * - cache.t2.micro is not recommended for production.
     * - For production, use replication groups + multi-AZ + automatic failover.
     */
    private CfnCacheCluster createRedisCluster() {

        /* Subnet group required by ElastiCache.
         Defines which subnets Redis instances can run in */
        CfnSubnetGroup redisSubnetGroup = CfnSubnetGroup.Builder
                .create(this,"RedisSubnetGroup")
                .description("Redis/elasticache subnet group")
                .subnetIds(vpc.getPrivateSubnets().stream()
                        .map(ISubnet::getSubnetId)
                        .collect(Collectors.toList()))
                .build();

        // Create Redis cluster
        return CfnCacheCluster.Builder.create(this,"RedisCluster")
                .cacheNodeType("cache.t2.micro")
                .engine("redis")
                .numCacheNodes(1)
                // Attach cluster to private subnet group
                .cacheSubnetGroupName(redisSubnetGroup.getCacheSubnetGroupName())
                // Allow ECS services inside VPC to access Redis
                .vpcSecurityGroupIds(List.of(vpc.getVpcDefaultSecurityGroup()))
                .build();
    }


    /**
     * Creates Grafana dashboard service on ECS Fargate.

     * Purpose:
     * - Provides visualization layer for monitoring metrics
     * - Connects to Prometheus as a data source
     * - Used for dashboards, alerts, and observability

     * Architecture:
     * - Runs as a containerized Grafana instance
     * - Exposed publicly through an Application Load Balancer

     * Port:
     * - 3000 -> Default Grafana web UI port

     * Current setup:
     * - Single Grafana container
     * - Minimal CPU/Memory allocation for development/testing

     * - For production:
     *   - Store credentials in AWS Secrets Manager
     */
    private ApplicationLoadBalancedFargateService createGrafanaService() {

        // ECS task definition for Grafana container
        FargateTaskDefinition taskDefinition = FargateTaskDefinition.Builder
                .create(this, "GrafanaService")
                .cpu(256)
                .memoryLimitMiB(512)
                .build();

        // Add Grafana container to task definition
        taskDefinition.addContainer("GrafanaContainer", ContainerDefinitionOptions.builder()
                         // Official Grafana Docker image
                        .image(ContainerImage.fromRegistry("grafana/grafana"))
                         // Expose Grafana UI on port 3000
                        .portMappings(List.of(PortMapping.builder()
                                        .containerPort(3000)
                                .build()))
                .build());

        // Create ECS Fargate service with public load balancer
        ApplicationLoadBalancedFargateService service = ApplicationLoadBalancedFargateService.Builder
                .create(this, "GrafanaUIService")
                .taskDefinition(taskDefinition)
                // Expose Grafana publicly through ALB
                .publicLoadBalancer(true)
                .listenerPort(3000)
                // Run single Grafana instance
                .desiredCount(1)
                .build();

        return service;
    }

    /**
     * CDK entry point.
     */
    public static void main(final String[] args) {
        App app = new App(AppProps.builder().outdir("./cdk.out").build());

        StackProps props = StackProps.builder()
                .synthesizer(new BootstraplessSynthesizer())
                .build();

        new LocalStack(app, "localstack", props);
        app.synth();

        System.out.println("App synthesizing in progress...");
    }
}
