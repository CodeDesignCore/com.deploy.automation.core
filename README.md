# Deploy Automation Core

Internal deployment automation library for standardizing build and release processes across Jenkins pipelines.

## Overview

`deploy-automation-core` provides a unified framework for managing deployment workflows, artifact publishing, and environment promotion in our Jenkins-based CI/CD infrastructure. This library abstracts common deployment patterns and ensures consistency across all product teams.

## Features

- **Automated Artifact Publishing** - Streamlined publishing to Nexus/Artifactory with metadata tagging
- **Environment Promotion** - Controlled promotion workflows (DEV → QA → STAGING → PROD)
- **Rollback Management** - Quick rollback capabilities with version tracking
- **Build Metadata Collection** - Automatic collection of Git commit info, build timestamps, and dependencies
- **Jenkins Integration** - Native integration with Jenkins Pipeline DSL
- **Deployment Validation** - Pre and post-deployment health checks
- **Notification Framework** - Slack/Teams/Email notifications for deployment events
- **Audit Logging** - Comprehensive audit trail for compliance requirements

## Installation

### Maven

Add to your `pom.xml`:

```xml
<dependency>
    <groupId>com.deploy.automation</groupId>
    <artifactId>core</artifactId>
    <version>3.2.1</version>
</dependency>
```

### Gradle

Add to your `build.gradle`:

```groovy
implementation 'com.deploy.automation:core:3.2.1'
```

## Configuration

Create a `deployment-config.yaml` in your project root:

```yaml
deployment:
  application: "customer-service"
  team: "platform-engineering"
  environments:
    - name: "dev"
      url: "https://dev.internal.company.com"
      auto-deploy: true
    - name: "staging"
      url: "https://staging.internal.company.com"
      auto-deploy: false
      approvers: ["tech-lead", "product-owner"]
    - name: "production"
      url: "https://prod.company.com"
      auto-deploy: false
      approvers: ["engineering-manager", "site-reliability"]
  
  artifacts:
    repository: "https://nexus.internal.company.com/repository/releases"
    retention-days: 90
    
  notifications:
    slack-channel: "#deployments"
    on-failure: true
    on-success: false
```

## Jenkins Pipeline Integration

### Declarative Pipeline Example

```groovy
@Library('deploy-automation-core') _

pipeline {
    agent any
    
    stages {
        stage('Build') {
            steps {
                sh 'mvn clean package'
            }
        }
        
        stage('Publish Artifacts') {
            steps {
                script {
                    deployCore.publishArtifact([
                        artifactPath: 'target/*.jar',
                        version: env.BUILD_NUMBER,
                        metadata: [
                            commit: env.GIT_COMMIT,
                            branch: env.GIT_BRANCH,
                            buildUrl: env.BUILD_URL
                        ]
                    ])
                }
            }
        }
        
        stage('Deploy to Dev') {
            steps {
                script {
                    deployCore.deploy([
                        environment: 'dev',
                        version: env.BUILD_NUMBER,
                        healthCheck: true
                    ])
                }
            }
        }
        
        stage('Promote to Staging') {
            when {
                branch 'main'
            }
            steps {
                script {
                    deployCore.promote([
                        from: 'dev',
                        to: 'staging',
                        version: env.BUILD_NUMBER,
                        requireApproval: true
                    ])
                }
            }
        }
    }
    
    post {
        failure {
            script {
                deployCore.notifyFailure([
                    channel: '#deployments',
                    mention: '@platform-team'
                ])
            }
        }
    }
}
```

### Scripted Pipeline Example

```groovy
@Library('deploy-automation-core') _

node {
    stage('Checkout') {
        checkout scm
    }
    
    stage('Build & Test') {
        sh 'mvn clean verify'
    }
    
    stage('Deploy') {
        def deploymentResult = deployCore.executeDeployment([
            environment: params.ENVIRONMENT,
            version: params.VERSION,
            dryRun: params.DRY_RUN
        ])
        
        if (deploymentResult.success) {
            echo "Deployment successful: ${deploymentResult.deploymentId}"
        } else {
            error "Deployment failed: ${deploymentResult.errorMessage}"
        }
    }
}
```

## API Usage

### Programmatic Deployment

```java
import com.deploy.automation.core.DeploymentManager;
import com.deploy.automation.core.config.DeploymentConfig;
import com.deploy.automation.core.model.DeploymentRequest;

public class DeploymentExample {
    public static void main(String[] args) {
        DeploymentConfig config = DeploymentConfig.fromFile("deployment-config.yaml");
        DeploymentManager manager = new DeploymentManager(config);
        
        DeploymentRequest request = DeploymentRequest.builder()
            .environment("staging")
            .version("1.2.3")
            .artifactId("customer-service")
            .healthCheckEnabled(true)
            .rollbackOnFailure(true)
            .build();
            
        DeploymentResult result = manager.deploy(request);
        
        if (result.isSuccessful()) {
            System.out.println("Deployment ID: " + result.getDeploymentId());
        } else {
            System.err.println("Deployment failed: " + result.getErrorMessage());
            manager.rollback(result.getDeploymentId());
        }
    }
}
```

## Environment Variables

The library respects the following environment variables:

| Variable | Description | Default |
|----------|-------------|---------|
| `DEPLOY_CONFIG_PATH` | Path to deployment config file | `./deployment-config.yaml` |
| `NEXUS_URL` | Artifact repository URL | From config |
| `NEXUS_USERNAME` | Repository username | From Jenkins credentials |
| `NEXUS_PASSWORD` | Repository password | From Jenkins credentials |
| `DEPLOY_DRY_RUN` | Enable dry-run mode | `false` |
| `DEPLOY_TIMEOUT` | Deployment timeout in seconds | `600` |
| `HEALTH_CHECK_RETRIES` | Number of health check retries | `5` |

## Common Use Cases

### Rolling Back a Deployment

```groovy
deployCore.rollback([
    environment: 'production',
    toVersion: '2.1.4',
    reason: 'Critical bug in 2.1.5'
])
```

### Blue-Green Deployment

```groovy
deployCore.blueGreenDeploy([
    environment: 'production',
    version: env.BUILD_NUMBER,
    switchTraffic: false  // Manual traffic switch
])
```

### Canary Release

```groovy
deployCore.canaryDeploy([
    environment: 'production',
    version: env.BUILD_NUMBER,
    trafficPercentage: 10,
    duration: '30m'
])
```

## Troubleshooting

### Deployment Fails with "Artifact Not Found"

Ensure your artifact was successfully published to Nexus:
```bash
curl -u $NEXUS_USERNAME:$NEXUS_PASSWORD \
  https://nexus.internal.company.com/repository/releases/com/company/customer-service/1.2.3/
```

### Health Checks Failing

Increase timeout or retry count:
```yaml
deployment:
  health-check:
    timeout: 120
    retries: 10
    endpoint: "/actuator/health"
```

### Permission Denied Errors

Verify your Jenkins credentials have access to:
- Nexus repository (read/write)
- Target deployment environments
- Kubernetes/Docker registry (if applicable)

## Version Compatibility

| Core Version | Jenkins Version | Java Version | Spring Boot |
|--------------|----------------|--------------|-------------|
| 3.x          | 2.300+         | 11+          | 2.5+        |
| 2.x          | 2.200+         | 8+           | 2.3+        |
| 1.x          | 2.100+         | 8+           | 2.0+        |

## Contributing

This is an internal library maintained by the Platform Engineering team.

For bug reports or feature requests:
1. Create a ticket in JIRA: [DEVOPS project](https://jira.internal.company.com/projects/DEVOPS)
2. Reach out in Slack: `#platform-engineering`
3. Email: platform-team@company.com

For urgent production issues, page the on-call engineer via PagerDuty.

## Support

- **Documentation**: [Confluence Wiki](https://wiki.internal.company.com/display/DEVOPS/Deploy+Automation+Core)
- **Runbooks**: [GitLab](https://gitlab.internal.company.com/platform/runbooks)
- **Slack**: `#platform-engineering` or `#deployments`
- **On-call**: Use PagerDuty with service key `deploy-automation`

## License

Internal use only. Copyright © 2024 Company Engineering.
