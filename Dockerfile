FROM public.ecr.aws/lambda/java:8

COPY target/classes ${LAMBDA_TASK_ROOT}
COPY target/dependency/* ${LAMBDA_TASK_ROOT}/lib/

CMD [ "com.sonatype.LambdaHandler::handleRequest" ]
