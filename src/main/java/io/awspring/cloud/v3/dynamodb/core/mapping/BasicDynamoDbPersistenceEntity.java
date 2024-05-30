package io.awspring.cloud.v3.dynamodb.core.mapping;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.expression.BeanFactoryAccessor;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.data.mapping.MappingException;
import org.springframework.data.mapping.model.BasicPersistentEntity;
import org.springframework.data.util.TypeInformation;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.Optional;

public class BasicDynamoDbPersistenceEntity<T> extends BasicPersistentEntity<T, DynamoDbPersistentProperty> implements DynamoDbPersistenceEntity<T>, ApplicationContextAware {

    private static final DynamoDbPersistentEntityMetadataVerifier DEFAULT_VERIFIER = new CompositeDynamoDbPersistentEntityMetadataVerifier();

    private @Nullable StandardEvaluationContext spelContext;
    private DynamoDbPersistentEntityMetadataVerifier verifier = DEFAULT_VERIFIER;

    public String tableName;
    private NamingStrategy namingStrategy = NamingStrategy.INSTANCE;

    public BasicDynamoDbPersistenceEntity(TypeInformation<T> typeInformation, DynamoDbPersistentEntityMetadataVerifier verifier) {

        super(typeInformation, DynamoDbPersistentPropertyComparator.INSTANCE);

        setVerifier(verifier);
    }

    public BasicDynamoDbPersistenceEntity(TypeInformation<T> information, Comparator<DynamoDbPersistentProperty> comparator) {
        super(information, comparator);
    }

    private String determineTableName() {
        Table annotation = findAnnotation(Table.class);

        if (annotation != null && StringUtils.hasText(annotation.value())) {
            return annotation.value();
        }
        return namingStrategy.getTableName(this);
    }


    public DynamoDbPersistentEntityMetadataVerifier getVerifier() {
        return verifier;
    }

    public void setVerifier(DynamoDbPersistentEntityMetadataVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    public void verify() throws MappingException {

        super.verify();

        this.verifier.verify(this);

        if (this.tableName == null) {
            setTableName(determineTableName());
        }
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    @Override
    public String getTableName() {
        return Optional.ofNullable(this.tableName).orElseGet(this::determineTableName);
    }

    @Override
    public void setApplicationContext(ApplicationContext context) throws BeansException {
        Assert.notNull(context, "ApplicationContext must not be null");

        spelContext = new StandardEvaluationContext();
        spelContext.addPropertyAccessor(new BeanFactoryAccessor());
        spelContext.setBeanResolver(new BeanFactoryResolver(context));
        spelContext.setRootObject(context);
    }

    public NamingStrategy getNamingStrategy() {
        return namingStrategy;
    }

    public void setNamingStrategy(NamingStrategy namingStrategy) {
        this.namingStrategy = namingStrategy;
    }
}
