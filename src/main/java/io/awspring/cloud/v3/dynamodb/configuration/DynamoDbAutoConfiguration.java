package io.awspring.cloud.v3.dynamodb.configuration;

import io.awspring.cloud.v3.dynamodb.configuration.core.CredentialsProviderAutoConfiguration;
import io.awspring.cloud.v3.dynamodb.configuration.core.RegionProviderAutoConfiguration;
import io.awspring.cloud.v3.dynamodb.configuration.core.SpringCloudClientConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;

@EnableConfigurationProperties(DynamoDbProperties.class)
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter({ CredentialsProviderAutoConfiguration.class, RegionProviderAutoConfiguration.class })
@ConditionalOnProperty(name = "spring.cloud.aws.dynamodb.enabled", havingValue = "true", matchIfMissing = true)
public class DynamoDbAutoConfiguration {

	private final DynamoDbProperties properties;

	public DynamoDbAutoConfiguration(DynamoDbProperties properties) {
		this.properties = properties;
	}

	@Bean
	public DynamoDbClient dynamoDbClient() {
		DynamoDbClientBuilder dynamoDbClientBuilder = DynamoDbClient.builder();
		dynamoDbClientBuilder.overrideConfiguration(SpringCloudClientConfiguration.clientOverrideConfiguration());

		if(properties.getRegion() != null) {
			dynamoDbClientBuilder.region(Region.of(properties.getRegion()));
		}
		if(properties.getEndpoint() != null) {
			dynamoDbClientBuilder.endpointOverride(properties.getEndpoint());
		}
		return dynamoDbClientBuilder.build();
	}

}
