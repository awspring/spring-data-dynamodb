package io.awspring.cloud.v3.dynamodb;

import org.junit.Rule;
import org.junit.jupiter.api.AfterAll;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;

public class LocalStackTestContainer {
	static DockerImageName localstackImage = DockerImageName.parse("localstack/localstack:latest");

	@Rule
	public static LocalStackContainer localstack;

	{
		localstack = new LocalStackContainer(localstackImage)
			.withServices(LocalStackContainer.Service.DYNAMODB).withReuse(true);
		localstack.start();
	}

	@AfterAll
	public static void shutDown() {
		localstack.stop();
	}
}
