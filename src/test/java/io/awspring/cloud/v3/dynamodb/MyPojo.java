package io.awspring.cloud.v3.dynamodb;

import io.awspring.cloud.v3.dynamodb.core.mapping.PartitionKey;
import io.awspring.cloud.v3.dynamodb.core.mapping.Table;

@Table("myPojo")
public class MyPojo {

    @PartitionKey
    private String id;
    public MyPojo2 myPojo2;

    MyPojo() {
        id = "2";
        myPojo2 = new MyPojo2();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public MyPojo2 getMyPojo2() {
        return myPojo2;
    }

    public void setMyPojo2(MyPojo2 myPojo2) {
        this.myPojo2 = myPojo2;
    }
}