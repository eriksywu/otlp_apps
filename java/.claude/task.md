# Task directives:

- Use the subagent "java-pro" defined in the file java-pro.md in this directory
- Assume me (the user) is not familiar with Java and its ecosystem
- Thus do not prompt for any code proposals - I won't know if the code is legit or not anyways
- My machine does not have any toolchains installed to develope and run Java apps. Come up with the smallest set of tools to be able to compile and run a java app. Prefer to build and run via docker if possible.

# Task background:

I need a java app to test the behaviour of the java OTLP SDK (https://github.com/open-telemetry/opentelemetry-java/tree/main), in particular how it emits http duration histogram metrics and what instrumentation scope attributes are decorated into the emitted OTLP metrics. I will refer to as the proposed java app as "testApp" from here on out.

It seems there is a "no-code" java agent available to auto-instrument any java app without having to add any metrics code in the testApp itself: https://opentelemetry.io/docs/zero-code/java/agent/getting-started/


# testApp requirements
- The testApp shall just be a simple http server that transparently accepts any incoming request to any URL
- add a synthetic request latency to every request, up to 5secs, chosen randomly
- return status OK for 50% of requests, a 4xx code for 25% of requests and a 5xx code for the last 25% requests, chosen at random
- does not need to handle concurrent http requests and can be inmplemented with the assumption that the qps will be low - around 1qps at most.
- deployable on Kubernetes for amd64 and arm architecture. The deployment pattern can be assumed to be a single pod rep with some exposed http container port. The app http port shall be mapped to a cluster service called "testapp-service".


# Task objectives

1. Environment setup: Understand the requirements of the testApp and propose the set of toolchains needed to execute this task and how to install them. Prefer docker-ized toolchains if possible.
2. Implementaiton: After the necessary toolchains are installed, implement the testApp based on the requirements.
3. Dockerize the testApp so it can be run on a container environment
4. Prompt me to build and push the docker images to a remote docker repo. Ask me to provide the remote docker image path.
5. Generate a k8s deployment yaml for the testApp using the remote docker image