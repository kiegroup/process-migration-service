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

### JVM Build

```shell script
mvn clean package
```

### Native Build

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
    user-providers-directory: providers (1)
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
    migrate-at-start: true (2)
    baseline-on-migrate: true
    baseline-version: 1.0
    baseline-description: PimDB      
# Quartz configuration
  quartz:
    store-type: jdbc-cmt
    start-mode: forced
  resteasy:
    path: /rest (3)
  datasource:
    db-kind: h2 (4)
    jdbc:
      url: jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
    username: sa
    password: sa
  hibernate-orm:
    database:
      generation: update
pim:
  auth-method: file (5)
```

1. In case we wanted to enable a different database requiring a license agreement, the JDBC driver will have to be located in this `providers` folder manually.
2. Out of the box, Flyway will automatically create PIM database schema for H2 in-memory database. Disable it (`-Dquarkus.flyway.migrate-at-start=false`) when you do not want PIM to handle the schema creation.
3. Deploy the application on `/rest`
4. H2 in-memory datasource. Override it by one of your choice
5. Authentication method. Defaults to `file` but `jdbc` or `ldap` are also valid options

### Configuration overrides

Check [Quarkus Configuration Reference Guide](https://quarkus.io/guides/config-reference) for further details.

It is possible to override or extend the provided configuration. You can provide one or more additional configuration 
files that will allow you to customize the application. Several examples are provided in the [examples](./examples) 
folder.

As an example, if you want to replace the H2 default persistence configuration by 
[MariaDB](./examples/persistence/mariadb.yml) and the authentication mechanism to use 
[LDAP](examples/authentication/ldap/ldap.yml), see sample config files below.

- [MariaDB database configuration file](./examples/persistence/mariadb.yml)
- [LDAP configuration file](examples/authentication/ldap/ldap.yml)

**Note:** These files will override or extend the already defined properties in the application.yaml file

#### Using Keystore Vault

This feature uses the [Quarkiverse File Vault](https://github.com/quarkiverse/quarkus-file-vault) extension.
By default, the Credentials Provider interface supports some credential consumer extensions like `agroal`, used
for database configuration.
In Process Instance Migration we have added support for KIE Server basic authentication and client certificate 
definition.

##### Usage

Add passwords to the keystore that will be used as Vault

```shell
keytool -importpass -alias pimdb -keystore pimvault.p12 -storepass password -storetype PKCS12 
keytool -importpass -alias kieserver -keystore pimvault.p12 -storepass password -storetype PKCS12
keytool -importpass -alias cert -keystore pimvault.p12 -storepass password -storetype PKCS12
keytool -importpass -alias keystore -keystore pimvault.p12 -storepass password -storetype PKCS12
keytool -importpass -alias truststore -keystore pimvault.p12 -storepass password -storetype PKCS12
```

Configure the vault provider to use the keystore we just created

```yaml
quarkus:
  file:
    vault:
      provider:
        pim:
          path: pimvault.p12
          secret: ${vault.storepassword} # This will be provided as a property
```

Configure your application to use the credentials from the vault

```yaml
quarkus:
  datasource:
    credentials-provider: quarkus.file.vault.provider.pim.pimdb
kieservers:
  - host: http://localhost:18080/kie-server/services/rest/server
    credentials-provider: quarkus.file.vault.provider.pim.kieserver
```

##### Masking the Vault password

This is a feature introduced in [Quarkus-File-Vault 0.7.1](https://github.com/quarkiverse/quarkus-file-vault/tree/0.7.1)
where a symetric password can be used to add an additional indirection to the stored passwords and certificates.

By using the [vault-utils](https://github.com/quarkiverse/quarkus-file-vault/tree/0.7.1/vault-utils) tool it is possible to
encrypt using an encryption key (a default will be generated if none is provided).

In order to use the vault-utils, clone the repository and build the project as an uber-jar.

```bash
git clone https://github.com/quarkiverse/quarkus-file-vault.git
cd quarkus-file-vault/vault-utils
git checkout 0.7.1
mvn clean package -Dquarkus.package.type=uber-jar
```

Now use the generated artifact to encrypt the passwords:

```bash
$ java -jar target/quarkus-file-vault-utils-0.7.1-runner.jar -p vaultpassword
###########################################################################################################
Please, add the following parameters to your application.properties file, and replace the <keystore-name> !
quarkus.file.vault.provider.<keystore-name>.encryption-key=Y2UaDd4T6QFpmirTQjhb6A
quarkus.file.vault.provider.<keystore-name>.secret=DBQLBMTjbTO1rYbSRu82DeTqcp0YgOAEFfzRVx_kJJnG0dPaBZfgolg8
###########################################################################################################
```

The output can be translated into yaml and used into the application.yaml file like this:

```yaml
quarkus:
  file:
    vault:
      provider:
        pim:
          path: config/pimvault.p12
          encryption-key: ${vault.encryption-key}
          secret: DBQLBMTjbTO1rYbSRu82DeTqcp0YgOAEFfzRVx_kJJnG0dPaBZfgolg8
```

Finally, the application can be started as follows:

```bash
java -jar -Dvault.encryption-key=vaultpassword target/quarkus-app/quarkus-run.jar
```

#### Defining KIE Servers

The right way to configure the connection to one or more KIE Servers in order to perform the migrations, a list of 
KIE servers should exist in the configuration file. 

##### Basic Authentication

[Example](examples/kieservers/basic-auth.yml)

```yaml
kieservers:
  - host: http://kieserver1.example.com:8080/kie-server/services/rest/server
    username: joe
    password: secret
  - host: http://kieserver2.example.com:8080/kie-server/services/rest/server
    username: jim
    password: secret
```

##### Token Based Authentication

[Example](examples/kieservers/token.yml)

```yaml
kieservers:
- host: http://localhost:18080/kie-server/services/rest/server
  token: __REDACTED__
```

##### Credentials Provider

Don't forget to configure the Vault. See [Configuring a File Vault](#using-keystore-vault). If the `credentials-provider` property is used
the other properties will be ignored. The vault key will be the username and the value will be the password.

[Example](examples/kieservers/basic-auth-vault.yml)

```yaml
kieservers:
  - host: http://localhost:18080/kie-server/services/rest/server
    credentials-provider: quarkus.file.vault.provider.pim.kieserver
```

##### Mutual TLS Authentication

[Example](examples/kieservers/client-cert.yml)

```yaml
kieservers:
  - host: http://localhost:18080/kie-server/services/rest/server
# HTTP Basic or Token Authentication can still be used, but usually a client identity is sufficient.
#   username: foo
#   password: bar
client-cert:
  cert-name: pim
  cert-password: pim-secret
  keystore-path: /path/to/keystore.jks
  keystore-password: ks-secret
  truststore-path: /path/to/truststore.jks
  truststore-password: ts-secret
```

Or using vault

[Example](examples/kieservers/client-cert-vault.yml)

```yaml
kieservers:
  - host: http://localhost:18080/kie-server/services/rest/server
client-cert:
  cert-name: pim
  cert-credentials-provider: quarkus.file.vault.provider.pim.cert
  keystore-path: /path/to/keystore.jks
  keystore-credentials-provider: quarkus.file.vault.provider.pim.keystore
  truststore-path: /path/to/truststore.jks
  truststore-credentials-provider: quarkus.file.vault.provider.pim.truststore
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

By default, Quartz jobs are stored in-memory database through connections managed by the container, but it is possible to configure Quartz to persist the jobs in
a different way either using a user transaction based configuration or the in-memory database. This can be achieved by setting the `quarkus.quartz.store-type` property 
to `ram` or `jdbc-tx`. See the 
[examples](./examples/quartz) and the [Quarkus Quartz documentation](https://quarkus.io/guides/quartz).

## Using other JDBC extensions

The H2 JDBC extension is set by default. However, users will be able to use different JDBC extensions to connect to any
supported database. For that purpose you will need to follow these steps:

1. Create PIM schema database by using the right DDL scripts located at [ddl-scripts](ddl-scripts) folder. In case of using an existing PIM database schema, use the upgrade DDL scripts located at [upgrade-scripts](upgrade-scripts) folder.


2. Re-augment the base build to include the right build properties for the database. For instance, the below command will add a PostgreSQL database extension by re-augmenting the application.

    ```shell script
    java -jar -Dquarkus.launch.rebuild=true -Dquarkus.datasource.db-kind=postgresql target/quarkus-app/quarkus-run.jar
    ```
   Note that in case of using a database requiring a JDBC driver license agreement (such as Oracle, DB2 and MS SQL Server), we will need to drop the JDBC drivers in the `providers` folder manually before re-augmenting the Quarkus application.


3. Out of the box, PIM comes with an H2 in-memory database where the database schema is auto-created automatically for you. However, this feature is not supported for other certified databases. 
In that sense, you need to disable the database schema auto-creation feature by passing and setting up the `quarkus.flyway.migrate-at-start` property to `false` 
Afterwards, you can start up the application normally.
    ```shell script
    java -jar -Dquarkus.flyway.migrate-at-start=false -Dquarkus.datasource.username=jbpm -Dquarkus.datasource.password=jbpm -Dquarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/jbpm?pinGlobalTxToPhysicalConnection=true target/quarkus-app/quarkus-run.jar
    ```

Reference:

* [Quarkus Datasources](https://quarkus.io/guides/datasource#jdbc-datasource)
* [Quarkus Re-augmentation](https://quarkus.io/guides/reaugmentation)
* [Quarkus Flyway](https://quarkus.io/guides/flyway)

### How to change build time properties in a `mutable-jar`

By default, the Process Migration Service is built as a `mutable-jar` and configured to fail if any built-time
property is changed at runtime. If you want to change a build-time property it is required that you re-augment the
build.

```shell script
java -jar -Dquarkus.launch.rebuild=true -Dquarkus.datasource.db-kind=mariadb target/quarkus-app/quarkus-run.jar
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
