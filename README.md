# Process Instance Migration Service

This service is intended to be deployed as a standalone application. It must be integrated with one or more KIE Process 
Server instances to be able to execute migrations remotely.

The two entities that will be used are _Migration Plans_ and _Migrations_.

## Migration Plan

Is a definition of how the migration will be performed. Includes the following information:

* Plan ID (Generated)
* Plan name
* Plan description
* Source and target container IDs
* Node mappings (if any)

## Migration

Is the execution of a defined plan, applied to a set of process instances. These are the attributes that define a 
Migration:

* Migration ID (Generated)
* Plan ID
* Instances to migrate.
* Execution type
  * Sync or Async
  * Scheduled start time
  * Callback URL

## Requirements

* JRE 11+
* Running KIE Process server

### Requirements for testing

PIM Integration Tests requires an external Kie Server instance, for that you can provide your own instance
or use TestContainers. TestContainers on Podman requires to connect through the external API so make sure
you can connect remotely to Podman.

```shell script
# Use a different time but bear in mind the default is 5 seconds
$ podman system service --time=0 &

# Confirm it's working 
$ podman-remote info
...
  os: linux
  remoteSocket:
    exists: true
    path: /run/user/1000/podman/podman.sock
...
``` 

## Build

It is a [Quarkus 2.x](https://quarkus.io/) application.

### JVM

```shell script
mvn clean package
```

### Native

**Note**: Native compilation is currently not supported.

```shell script
mvn clean package -Pnative -Dquarkus.native.container-build=true
```

## Run application

### JVM 

```bash
java -jar target/quarkus-app/quarkus-run.jar
```

### Native

```shell script
./target/process-migration-runner
```

You can provide your custom configuration file. Check [application.yaml](src/main/resources/application.yaml) 
to see an example. The provided configuration will be added or override the one existing in application.yaml

### Container image

If you want to create a container image using your JVM or Native build use the `-Dquarkus.container-image.build=true` 
flag when packaging your application and pass in any other configuration property you want to override. See the 
[Container Image Guide](https://quarkus.io/guides/container-image#building).

```shell script 
$ mvn clean package -Dquarkus.container-image.build=true \
 -Dquarkus.container-image.push=true \
-Dquarkus.container-image.image=quay.io/kiegroup/process-migration-service:1.0
...
[INFO] [io.quarkus.container.image.jib.deployment.JibProcessor] Created container image quay.io/kiegroup/process-migration-service:1.0 (sha256:dc8028963923081941529ef0ea515bd1e970b8fc96d5ad6a9346eb4ad61028f6)
```

Or you can just use your favourite container builder tool and refer to any of the existing [Dockerfiles](./src/main/docker)

```shell script
$ podman build -t quay.io/kiegroup/process-migration-service:1.0 -f ./src/main/docker/Dockerfile.jvm .
```

## Configuration

Default configuration is as follows:

```yaml
quarkus:
  class-loading:
    removed-artifacts: com.oracle.database.jdbc:ojdbc8,com.ibm.db2:jcc,com.microsoft.sqlserver:mssql-jdbc
  package:
    type: mutable-jar
    user-providers-directory: providers
  http:
    auth:
      basic: true
      policy:
        main-policy:
          roles-allowed: admin
      permission:
        main:
          paths: /*
          policy: main-policy
        public:
          paths: /q/health/*
          policy: permit
          methods: GET
  security:
    users:
      file:
        realm-name: pim_file
        enabled: true
        plain-text: true
        users: users.properties
        roles: roles.properties
    jdbc:
      realm-name: pim_jdbc
      enabled: true
      principal-query:
        sql: SELECT u.password, u.role FROM users u WHERE u.username=?
    ldap:
      realm-name: pim_ldap
      dir-context:
        url: ldap://override-when-needed
      identity-mapping:
        search-base-dn: ou=users,o=YourCompany,c=ES
# Flyway to create PIM schema
  flyway:
    connect-retries: 10
    table: flyway_pim_history
    migrate-at-start: true
    baseline-on-migrate: true
    baseline-version: 1.0
    baseline-description: PimDB
    sql-migration-prefix: h2 (1)        
# Quartz configuration
  quartz:
    store-type: jdbc-cmt
    start-mode: forced
  resteasy:
    path: /rest (2)
  datasource:
    db-kind: h2 (3)
    jdbc:
      url: jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
    username: sa
    password: sa
  hibernate-orm:
    database:
      generation: update
pim:
  auth-method: file (4)
```

1. Flyway will automatically create PIM schema when enabled based on the DDL scripts prefix. Enabled by default.
2. Deploy the application on `/rest`
3. H2 in-memory datasource. Override it by one of your choice
4. Authentication method. Defaults to `file` but `jdbc` or `ldap` are also valid options

### Configuration overrides

Check [Quarkus Configuration Reference Guide](https://quarkus.io/guides/config-reference) for further details.

It is possible to override or extend the provided configuration. You can provide one or more additional configuration 
files that will allow you to customize the application. Several examples are provided in the [examples](./examples) 
folder.

As an example, if you want to replace the H2 default persistence configuration by 
[MariaDB](./examples/persistence/mariadb.yml) and the authentication mechanism to use 
[LDAP](examples/authentication/ldap/ldap.yml), see below.

**Note:** As the MariaDB jdbc driver is not included in the classpath. It must be added.

**Note:** These files will override or extend the already defined properties in the application.yaml file

#### Defining KIE Servers

The right way to configure the connection to one or more KIE Servers in order to perform the migrations, a list of 
KIE servers should exist in the configuration file. [Example](./examples/kieservers.yml)

```yaml
kieservers:
  - host: http://kieserver1.example.com:8080/kie-server/services/rest/server
    username: joe
    password: secret
  - host: http://kieserver2.example.com:8080/kie-server/services/rest/server
    username: jim
    password: secret
```

#### MariaDB Datasource

See [Using other JDBC extensions](#using-other-JDBC-extensions) for details on how to include additional JDBC drivers to the runtime.

```yaml
quarkus:
  datasource:
    db-kind: mariadb
    jdbc:
      url: jdbc:mariadb://localhost:3306/pimdb
    username: pim
    password: pim123
```

_Refer to the [Quarkus Datasource](https://quarkus.io/guides/datasource) configuration for further details_

#### Basic authentication and authorization

Authorization example available [here](./examples/authentication/basic-auth.yml). Shows how to define basic authorization.

**Note that this is a build time configuration** (See [Quarkus Authorization](https://quarkus.io/guides/security-authorization))

```yaml
  http:
    auth:
      basic: true
      policy:
        main-policy:
          roles-allowed: admin
      permission:
        main:
          paths: /*
          policy: main-policy
        public:
          paths: /q/health/*
          policy: permit
          methods: GET
```

1. Authentication type `BASIC`
1. Every resource under root path requires the `admin` role
1. Health checks are not secured

By default, the user properties IdentityProvider is provided in plain text:

**Note that this is a build time configuration** (See [Quarkus Security](https://quarkus.io/guides/security#identity-providers))

```yaml
  security:
    users:
      file:
        enabled: true
        plain-text: true
        users: users.properties
        roles: roles.properties
```

## Configuring Quartz

By default, Quartz jobs are stored in-memory database through connections managed by container, but it is possible to configure Quartz to persist the jobs in
a different way either using a user transaction based configuration or the in-memory database. This can be achieved by setting the `quarkus.quartz.store-type` property 
to `ram` or `jdbc-tx`. See the 
[examples](./examples/quartz) and the [Quarkus Quartz documentation](https://quarkus.io/guides/quartz).

## Using other JDBC extensions

The H2 JDBC extension is set by default. However, users will be able to use different JDBC extensions to connect to any
supported database. For that purpose you will have to re-augment the base build to include the right build properties.

For instance, below command will add a PostgreSQL database extension by re-augmenting the application.

```shell script
java -jar -Dquarkus.launch.rebuild=true -Dquarkus.datasource.db-kind=postgresql target/quarkus-app/quarkus-run.jar
```

Afterwards, you can start up the application normally.
```shell script
java -jar -Dquarkus.datasource.username=jbpm -Dquarkus.datasource.password=jbpm -Dquarkus.flyway.sql-migration-prefix=postgresql -Dquarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/jbpm?pinGlobalTxToPhysicalConnection=true target/quarkus-app/quarkus-run.jar
```
Also, note that Flyway prefix property `quarkus.flyway.sql-migration-prefix` must be set accordingly to the right database used.

Reference:

* [Quarkus Datasources](https://quarkus.io/guides/datasource#jdbc-datasource)
* [Quarkus Re-augmentation](https://quarkus.io/guides/reaugmentation)
* [Quarkus Flyway](https://quarkus.io/guides/flyway)

## Disabling database schema auto-creation

Process Migration service generates the database schema automatically if it is not already present by using the DDL scripts bundled in the application.
You should be able to disable and manage the schema creation by your own by disabling some Flyway properties.

```shell script
java -jar -Dquarkus.flyway.migrate-at-start=false target/quarkus-app/quarkus-run.jar
```


### How to change build time properties in a `mutable-jar`

By default, the Process Migration Service is built as a `mutable-jar` and configured to fail if any built-time
property is changed at runtime. If you want to change a build-time property it is required that you re-augment the
build.

```shell script
java -jar -Dquarkus.launch.rebuild=true -Dquarkus.datasource.db-kind=mariadb -Dquarkus.flyway.sql-migration-prefix=mariadb target/quarkus-app/quarkus-run.jar
```

## Usage

### Define the plan (without node mappings)

Request:

```bash
URL: http://localhost:8080/rest/plans
Method: POST
HTTP Headers:
  Content-Type: application/json
  Authorization: Basic a2VybWl0OnRoZWZyb2c=
Body:
{
    "name": "Test plan",
    "description": "Evaluation Process Test Plan",
    "source": {
      "containerId": "evaluation_1.0",
      "processId": "evaluation"
    },
    "target": {
      "containerId": "evaluation_1.1",
      "processId": "evaluation"
    }
}
```

Response:

```http
Status: 201 CREATED
HTTP Headers:
  Content-Type: application/json
Body:
{
    "id": 1,
    "name": "Test plan",
    "description": "Evaluation Process Test Plan",
    "source": {
      "containerId": "evaluation_1.0",
      "processId": "evaluation"
    },
    "target": {
      "containerId": "evaluation_1.1",
      "processId": "evaluation"
    }
}
```

### Deploy some processes to test the migration

1. Start a KIE Server
1. Deploy two versions of the evaluation project (evaluation_1.0 and evaluation_1.1)
1. Start one instance of the evaluation process (evaluation_1.0)

### Create a sync migration

```http
URL: http://localhost:8080/rest/migrations
Method: POST
HTTP Headers:
  Content-Type: application/json
  Authorization: Basic a2VybWl0OnRoZWZyb2c=
Body:
{
    "planId": 1,
    "processInstanceIds": [1],
    "kieserverId": "sample-server",
    "execution": {
      "type": "SYNC"
    }
}
```

Response:

```http
Status: 201 CREATED
HTTP Headers:
  Content-Type: application/json
Body:
{
    "id": 1,
    "definition": {
        "planId": 1,
        "processInstanceIds": [1],
        "kieserverId": "sample-server",
        "requester": "kermit",
        "execution": {
            "type": "SYNC"
        }
    },
    "createdAt": "2018-11-29T13:47:07.839Z",
    "startedAt": "2018-11-29T13:47:07.839Z",
    "finishedAt": "2018-11-29T13:47:07.874Z",
    "status": "COMPLETED"
}
```

As it is a Synchronous migration, the result of the migration will be returned once it has finished.

### Check the migration output

The following request will fetch the overall result of the migration

Request:

```http
URL: http://localhost:8080/rest/migrations/1
Method: GET
HTTP Headers:
  Content-Type: application/json
  Authorization: Basic a2VybWl0OnRoZWZyb2c=
```

Response:

```http
Status: 200 OK
HTTP Headers:
  Content-Type: application/json
Body:
{
    "id": 1,
    "definition": {
        "planId": 1,
        "processInstanceIds": [],
        "kieserverId": "sample-server",
        "requester": "kermit",
        "execution": {
            "type": "SYNC"
        }
    },
    "createdAt": "2018-11-27T14:28:58.918Z",
    "startedAt": "2018-11-27T14:28:59.861Z",
    "finishedAt": "2018-11-27T14:29:00.167Z",
    "status": "COMPLETED"
}
```

To retrieve the individual results of the migration of each process instance

Request:

```http
URL: http://localhost:8080/rest/migrations/1/results
Method: GET
HTTP Headers:
  Content-Type: application/json
  Authorization: Basic a2VybWl0OnRoZWZyb2c=
```

Response:

```http
Status: 200 OK
HTTP Headers:
  Content-Type: application/json
Body:
[
    {
        "id": 1,
        "migrationId": 3,
        "processInstanceId": 5,
        "startDate": "2018-12-18T11:16:26.779Z",
        "endDate": "2018-12-18T11:16:26.906Z",
        "successful": true,
        "logs": [
            "INFO Tue Dec 18 12:16:26 CET 2018 Variable instances updated = 1 for process instance id 5",
            "INFO Tue Dec 18 12:16:26 CET 2018 Node instances updated = 3 for process instance id 5",
            "INFO Tue Dec 18 12:16:26 CET 2018 Process instances updated = 1 for process instance id 5",
            "INFO Tue Dec 18 12:16:26 CET 2018 Task variables updated = 1 for process instance id 5",
            "INFO Tue Dec 18 12:16:26 CET 2018 Task audit updated = 1 for process instance id 5",
            "INFO Tue Dec 18 12:16:26 CET 2018 Tasks updated = 1 for process instance id 5",
            "INFO Tue Dec 18 12:16:26 CET 2018 Context info updated = 0 for process instance id 5",
            "INFO Tue Dec 18 12:16:26 CET 2018 Executor Jobs updated = 0 for process instance id 5",
            "WARN Tue Dec 18 12:16:26 CET 2018 Source and target process id is exactly the same (test.myprocess) it's recommended to use unique process ids",
            "INFO Tue Dec 18 12:16:26 CET 2018 Mapping: Node instance logs to be updated  = [1]",
            "INFO Tue Dec 18 12:16:26 CET 2018 Mapping: Node instance logs updated = 1 for node instance id 1",
            "INFO Tue Dec 18 12:16:26 CET 2018 Mapping: Task audit updated = 1 for task id 5",
            "INFO Tue Dec 18 12:16:26 CET 2018 Mapping: Task updated = 1 for task id 5",
            "INFO Tue Dec 18 12:16:26 CET 2018 Migration of process instance (5) completed successfully to process test.myprocess"
        ]
    },
    {
        "id": 2,
        "migrationId": 3,
        "processInstanceId": 6,
        "startDate": "2018-12-18T11:16:26.992Z",
        "endDate": "2018-12-18T11:16:27.039Z",
        "successful": true,
        "logs": [
            "INFO Tue Dec 18 12:16:27 CET 2018 Variable instances updated = 1 for process instance id 6",
            "INFO Tue Dec 18 12:16:27 CET 2018 Node instances updated = 3 for process instance id 6",
            "INFO Tue Dec 18 12:16:27 CET 2018 Process instances updated = 1 for process instance id 6",
            "INFO Tue Dec 18 12:16:27 CET 2018 Task variables updated = 1 for process instance id 6",
            "INFO Tue Dec 18 12:16:27 CET 2018 Task audit updated = 1 for process instance id 6",
            "INFO Tue Dec 18 12:16:27 CET 2018 Tasks updated = 1 for process instance id 6",
            "INFO Tue Dec 18 12:16:27 CET 2018 Context info updated = 0 for process instance id 6",
            "INFO Tue Dec 18 12:16:27 CET 2018 Executor Jobs updated = 0 for process instance id 6",
            "WARN Tue Dec 18 12:16:27 CET 2018 Source and target process id is exactly the same (test.myprocess) it's recommended to use unique process ids",
            "INFO Tue Dec 18 12:16:27 CET 2018 Mapping: Node instance logs to be updated  = [1]",
            "INFO Tue Dec 18 12:16:27 CET 2018 Mapping: Node instance logs updated = 1 for node instance id 1",
            "INFO Tue Dec 18 12:16:27 CET 2018 Mapping: Task audit updated = 1 for task id 6",
            "INFO Tue Dec 18 12:16:27 CET 2018 Mapping: Task updated = 1 for task id 6",
            "INFO Tue Dec 18 12:16:27 CET 2018 Migration of process instance (6) completed successfully to process test.myprocess"
        ]
    }
]
```

### Create an Async migration

1. Start two more processes
1. Trigger the migration of all the existing active processes

Request:

```http
URL: http://localhost:8080/rest/migrations
Method: POST
HTTP Headers:
  Content-Type: application/json
  Authorization: Basic a2VybWl0OnRoZWZyb2c=
Body:
{
    "planId": 1,
    "processInstanceIds": [],
    "kieserverId": "sample-server",
    "execution": {
      "type": "ASYNC",
      "scheduledStartTime": "2018-12-11T12:35:00.000Z"
    }
}
```

Response:

```http
Status: 202 Accepted
HTTP Headers:
  Content-Type: application/json
Body:
{
    "id": 2,
    "definition": {
        "execution": {
            "type": "ASYNC",
            "scheduledStartTime": "2018-12-11T12:35:00.000Z"
        },
        "planId": 1,
        "processInstanceIds": [],
        "kieServerId": "sample-server",
    },
    "status": "SCHEDULED",
    "createdAt": "2018-11-07T11:28:43.828Z",
    "startedAt": null,
    "finishedAt": null
}
```

The migration status can be checked using the migrations api with the id returned as done before

## User Interface

The Process Instance Migration User Interface can be accessed in the following URL

[http://localhost:8080/](http://localhost:8080/)
