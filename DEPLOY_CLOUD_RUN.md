# Deploying to Google Cloud Run

## Overview

This guide provides steps to deploy a Spring Boot application that uses the Hypernova Java client to Google Cloud Run. Cloud Run is a managed platform that enables you to run stateless containers that are invocable via HTTP requests.

## Prerequisites

*   **Google Cloud SDK (`gcloud`):** Installed and configured with your Google Cloud account. ([Installation Guide](https://cloud.google.com/sdk/docs/install))
*   **Docker:** Installed locally for building Docker images. ([Installation Guide](https://docs.docker.com/get-docker/))
    *   Alternatively, you can use Jib with Maven to build images directly without a local Docker daemon.
*   **Google Cloud Project:**
    *   A Google Cloud Project with billing enabled.
    *   Cloud Run API enabled.
    *   Artifact Registry API enabled (or Container Registry API if you prefer, though Artifact Registry is recommended).
*   **Hypernova Service:** A running Hypernova service instance that is accessible from the Google Cloud Run environment (e.g., running on another cloud service, a VM, or also on Cloud Run).

## Steps

### 1. Package the Application

First, build your Spring Boot application to create an executable JAR file:

```bash
mvn clean package
```

This command will typically produce a JAR file in the `target/` directory (e.g., `demo-0.0.1-SNAPSHOT.jar`).

### 2. Create a `Dockerfile`

Create a file named `Dockerfile` in the root of your project (`/app/Dockerfile`) with the following content:

```dockerfile
# Use an official OpenJDK runtime as a parent image
FROM openjdk:17-jdk-slim

# Set the working directory in the container
WORKDIR /app

# Argument to specify the JAR file path (Maven specific)
ARG JAR_FILE=target/*.jar

# Copy the executable JAR to the container
COPY ${JAR_FILE} app.jar

# Optional: Create a non-root user for better security
# RUN groupadd -r spring && useradd --no-log-init -r -g spring spring
# USER spring

# Specify the command to run on container start
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

# Optional: Expose the port your Spring Boot application runs on (default is 8080)
# EXPOSE 8080
```

**Notes:**
*   If your JAR file has a different name or path pattern, adjust the `ARG JAR_FILE` line accordingly.
*   Uncomment the `RUN`, `USER`, and `EXPOSE` lines if needed. Cloud Run automatically uses port 8080 by default for ingress.

### 3. Build the Docker Image

Navigate to your project's root directory (where the `Dockerfile` is located) and build the Docker image.

Replace `YOUR_PROJECT_ID` with your Google Cloud Project ID, `YOUR_REPO` with your Artifact Registry repository name, and `your-app-name` with your desired application name.

**Option A: Using Docker locally**

```bash
# Example for Artifact Registry: REGION-docker.pkg.dev/YOUR_PROJECT_ID/YOUR_REPO/your-app-name:v1
# Example for Container Registry: gcr.io/YOUR_PROJECT_ID/your-app-name:v1

export IMAGE_TAG=us-central1-docker.pkg.dev/YOUR_PROJECT_ID/YOUR_REPO/your-app-name:v1
# or for GCR: export IMAGE_TAG=gcr.io/YOUR_PROJECT_ID/your-app-name:v1

docker build -t ${IMAGE_TAG} .
```

**Option B: Using Jib (Maven plugin - no Docker daemon needed)**

If you prefer not to use a local Docker daemon, you can use the Jib Maven plugin. Add it to your `pom.xml`:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>com.google.cloud.tools</groupId>
            <artifactId>jib-maven-plugin</artifactId>
            <version>3.4.0</version> <!-- Check for the latest version -->
            <configuration>
                <to>
                    <image>${IMAGE_TAG}</image> <!-- Define IMAGE_TAG, e.g., using properties -->
                </to>
            </configuration>
        </plugin>
    </plugins>
</build>
```

And then build the image:

```bash
# Define IMAGE_TAG, e.g., in pom.xml <properties> or via command line
# mvn compile jib:dockerBuild -Dimage=${IMAGE_TAG}
# Or directly if configured in pom.xml
mvn compile com.google.cloud.tools:jib-maven-plugin:dockerBuild
```

### 4. Push the Image to Artifact Registry (or GCR)

**Configure Docker Authentication:**

If you are using Artifact Registry, configure Docker to authenticate with your Artifact Registry region (e.g., `us-central1`):

```bash
gcloud auth configure-docker us-central1-docker.pkg.dev # Replace us-central1 with your region
```
For Container Registry (`gcr.io`), it might be:
```bash
gcloud auth configure-docker
```

**Push the Image:**

```bash
docker push ${IMAGE_TAG}
```

### 5. Deploy to Cloud Run

Deploy your container image to Cloud Run using the `gcloud run deploy` command.

Replace placeholders like `your-app-name`, `YOUR_REGION`, `YOUR_PROJECT_ID`, `YOUR_REPO`, and `http://your-hypernova-service-url/batch` with your actual values.

```bash
SERVICE_NAME="your-app-name" # Choose a name for your Cloud Run service
REGION="us-central1" # Choose your preferred region
# IMAGE_TAG should be the same as used in the push step
# e.g., IMAGE_TAG="us-central1-docker.pkg.dev/YOUR_PROJECT_ID/YOUR_REPO/your-app-name:v1"
HYPERNOVA_URL="http://your-hypernova-service-url/batch" # IMPORTANT: Set this to your actual Hypernova URL

gcloud run deploy ${SERVICE_NAME} \
    --image ${IMAGE_TAG} \
    --platform managed \
    --region ${REGION} \
    --allow-unauthenticated \
    --set-env-vars HYPERNOVA_SERVICE_URL=${HYPERNOVA_URL} \
    --port 8080 # Specify the port your app listens on, if not default 8080
```

**Explanation of Flags:**

*   `--image`: Specifies the Docker image to deploy.
*   `--platform managed`: Uses the fully managed Cloud Run environment.
*   `--region`: Specifies the Google Cloud region where your service will run.
*   `--allow-unauthenticated`: Allows public access to your service. For restricted access, configure IAM or Cloud IAP. ([Security Documentation](https://cloud.google.com/run/docs/securing/overview))
*   `--set-env-vars`: Sets environment variables. `HYPERNOVA_SERVICE_URL` is crucial for the Hypernova client to connect to your Hypernova service. Your application's `application.properties` should be configured to read this environment variable (e.g., `hypernova.service.url=${HYPERNOVA_SERVICE_URL:http://localhost:3030/batch}`).
*   `--port`: The port your application listens on inside the container. Cloud Run directs external traffic to this port. Default is 8080.

### 6. Verify Deployment

After the deployment command completes, it will output a service URL. Open this URL in your web browser to verify that your application is running correctly.

Check the logs in Google Cloud Console (Cloud Run -> Your Service -> Logs) for any errors if the deployment is not working as expected.

## Further Configuration

Cloud Run offers many configuration options, including:

*   **CPU and Memory:** Adjust the allocated CPU and memory for your service.
*   **Min/Max Instances:** Configure auto-scaling parameters, including minimum and maximum instances.
*   **Concurrency:** Set the number of concurrent requests a single container instance can handle.
*   **Secrets Management:** Securely manage API keys or database passwords using Secret Manager.
*   **Custom Domains:** Map your own domain to the Cloud Run service.

For more advanced topics and detailed information, refer to the [official Google Cloud Run documentation](https://cloud.google.com/run/docs).

