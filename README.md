# ERD Plus Hibernate Tool
Simple tool that will take an exported ERD model designed in [ERD Plus](https://erdplus.com/) and generate opinionated hibernate beans.

## Build
Build the code by running the following on the command line:
```shell
mvn clean package
```

## Running
```shell
java -jar target/erdplus-hibernate-tools-*.jar <erdplus_export_file> <output_dir> <package_name>
```