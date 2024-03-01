# Demonstrator message ordering problem
This projects demonstrates the problem with message ordering
that was introduced between version 5.0.0 and 5.0.1.
The problem is present in versions 5.0.1, 5.0.2, 5.0.3 and 5.0.4.


## working:
```
./mvnw -Pworking test
```

```
<dependency>
    <groupId>com.google.cloud</groupId>
    <artifactId>spring-cloud-gcp-dependencies</artifactId>
    <version>5.0.0</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

## broken:
```
./mvnw -Pbroken test
```

```
<dependency>
    <groupId>com.google.cloud</groupId>
    <artifactId>spring-cloud-gcp-dependencies</artifactId>
    <version>5.0.1</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>

```

## workaround 
Pinning google-cloud-pubsub-bom to version 1.125.13, which is used in 5.0.0
```
./mvnw -Pworkaround test
```

```
<dependency>
    <groupId>com.google.cloud</groupId>
    <artifactId>google-cloud-pubsub-bom</artifactId>
    <version>1.125.13</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>

<dependency>
    <groupId>com.google.cloud</groupId>
    <artifactId>spring-cloud-gcp-dependencies</artifactId>
    <version>5.0.1</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>

```