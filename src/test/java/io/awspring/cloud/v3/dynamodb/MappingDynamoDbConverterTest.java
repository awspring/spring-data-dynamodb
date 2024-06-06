package io.awspring.cloud.v3.dynamodb;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.v3.dynamodb.core.converter.MappingDynamoDbConverter;
import io.awspring.cloud.v3.dynamodb.core.mapping.DynamoDbMappingContext;
import io.awspring.cloud.v3.dynamodb.core.mapping.InnerClass;
import io.awspring.cloud.v3.dynamodb.core.mapping.PartitionKey;
import io.awspring.cloud.v3.dynamodb.core.mapping.Table;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.LocalDate;
import java.util.*;


public class MappingDynamoDbConverterTest {


	private DynamoDbMappingContext mappingContext;
	private MappingDynamoDbConverter mappingDynamoDbConverter;


	@BeforeEach
	void setUp() {
		this.mappingContext = new DynamoDbMappingContext();
		this.mappingDynamoDbConverter = new MappingDynamoDbConverter(mappingContext, new ObjectMapper());
		this.mappingDynamoDbConverter.afterPropertiesSet();
	}

	@Test
	void insertTestClass() {
		LocalDate testDate = LocalDate.now();
		TestClass testClassToBeInserted = new TestClass("testID", testDate, Arrays.asList("test1", "test2"), Collections.singletonList(new TelephoneNumber("099")));
		Map<String, AttributeValue> mapToBeChecked = new HashMap<>();
		mappingDynamoDbConverter.write(testClassToBeInserted, mapToBeChecked);
		Assertions.assertEquals(mapToBeChecked.get("id").s(), "testID");
		Assertions.assertEquals(mapToBeChecked.get("value").s() , testDate.toString());
		Assertions.assertEquals(mapToBeChecked.get("myList").l().size(),2);
	}

	@Table(tableName = "someTableName")
	public static class NewClassTest {
		@PartitionKey
		private String id;


		private TelephoneNumber telephoneNumber;

		public NewClassTest(String id,TelephoneNumber telephoneNumber) {
			this.telephoneNumber = telephoneNumber;
			this.id = id;
		}

		public TelephoneNumber getTelephoneNumber() {
			return telephoneNumber;
		}

		public void setTelephoneNumber(TelephoneNumber telephoneNumber) {
			this.telephoneNumber = telephoneNumber;
		}

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}
	}


	@Table(tableName = "someTableName")
	public static class TestClass {

		@PartitionKey
		private String id;

		private LocalDate value;

		private List<String> myList;

		private List<TelephoneNumber> telephoneNumber;

		@InnerClass
		private MyPojo2 myPojo;

		public TestClass() {
		}

		public TestClass(String id, LocalDate value) {
			this.id = id;
			this.value = value;
			this.myPojo = new MyPojo2();
			this.myList = new ArrayList<>();
			myList.add("test");
		}

		public TestClass(String id, LocalDate value, List<String> myList) {
			this.myList = myList;
			this.id = id;
			this.value = value;
		}

		public TestClass(String id, LocalDate value, List<String> myList, List<TelephoneNumber> telephoneNumber) {
			this.telephoneNumber = telephoneNumber;
			this.myList = myList;
			this.id = id;
			this.value = value;
			this.myPojo = new MyPojo2();
		}

		public List<TelephoneNumber> getTelephoneNumber() {
			return telephoneNumber;
		}

		public void setTelephoneNumber(List<TelephoneNumber> telephoneNumber) {
			this.telephoneNumber = telephoneNumber;
		}

		public List<String> getMyList() {
			return myList;
		}

		public void setMyList(List<String> myList) {
			this.myList = myList;
		}

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public LocalDate getValue() {
			return value;
		}

		public void setValue(LocalDate value) {
			this.value = value;
		}

		public MyPojo2 getMyPojo() {
			return myPojo;
		}

		public void setMyPojo(MyPojo2 myPojo) {
			this.myPojo = myPojo;
		}
	}

	public static class TelephoneNumber {
		private String telephone;

		public TelephoneNumber() {
		}

		public TelephoneNumber(String telephone) {
			this.telephone = telephone;
		}

		public String getTelephone() {
			return telephone;
		}

		public void setTelephone(String telephone) {
			this.telephone = telephone;
		}
	}

}
