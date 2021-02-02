# tool-maven-plugin-appinstall
Maven plugin for collecting information about the assembly of the application

# Особенности на текущий момент:
- Работает с приложениями из `SVN` репозитория. При работе с Git некоторая информация о версии приложения не будет собрана.
- Допускает использование как для прежней структуры приложения (с интерфейсом только на GWT), так и для современной структуры (GWT + React + service-rest).
- Реализован автоматический сбор информации об используемых библиотеках (возможно потребует согласования/корректировки).


```
c:\work\workspace\JepRiaShowcase\Tag\10.11.0\App\
```
- Подпапка `Doc` проекта должна содержать файл `map.xml` с описанием `DB` структуры. Например:
```
c:\work\workspace\JepRiaShowcase\Tag\10.11.0\Doc\map.xml
```
- Предварительно на целевом экземпляре `Tomcat` необходимо развернуть приложение `ModuleInfo`.

# Использование в тестовом режиме (прямой запуск плагина):
- Разместить в подпапке `App` вспомогательный `pom.xml` с настройками плагина `actuator-maven-plugin` или добавить настройки плагина в уже имеющийся `pom.xml` проекта. Примерное содержание `pom.xml`:
```
<?xml version="1.0" encoding="UTF-8"?>

<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <!-- 
    Временный помник для тестирования функционала Actuator Maven Plugin-а.
  -->

  <!-- Эти параметры должны в точности совпадать с аналогичными параметрами во всех дочерних pom.xml -->
  <groupId>org.jepria.jepriashowcase</groupId>
  <artifactId>JepRiaShowcase</artifactId>
  <version>12.0.0</version>
  <packaging>pom</packaging>

  <name>JepRiaShowcase</name>

  <properties>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <maven.compiler.source>1.8</maven.compiler.source>
    <maven.compiler.target>1.8</maven.compiler.target>
    <project.build.finalName>JepRiaShowcase</project.build.finalName>
  </properties>

  <build>
    <pluginManagement>
      <plugins>
        <plugin>
          <artifactId>maven-clean-plugin</artifactId>
          <version>3.1.0</version>
        </plugin>
        <plugin>
          <artifactId>maven-resources-plugin</artifactId>
          <version>3.1.0</version>
        </plugin>
        <plugin>
          <artifactId>maven-compiler-plugin</artifactId>
          <version>3.8.0</version>
        </plugin>
        <plugin>
          <artifactId>maven-surefire-plugin</artifactId>
          <version>2.22.1</version>
        </plugin>
        <plugin>
          <artifactId>maven-jar-plugin</artifactId>
          <version>3.0.2</version>
        </plugin>
        <plugin>
          <artifactId>maven-install-plugin</artifactId>
          <version>2.5.2</version>
        </plugin>
        <plugin>
          <artifactId>maven-deploy-plugin</artifactId>
          <version>2.8.2</version>
        </plugin>
        <plugin>
          <artifactId>maven-site-plugin</artifactId>
          <version>3.7.1</version>
        </plugin>
        <plugin>
          <artifactId>maven-project-info-reports-plugin</artifactId>
          <version>3.0.0</version>
        </plugin>
      </plugins>
    </pluginManagement>
    <plugins>
      <plugin>
        <groupId>org.jepria.tools.mavenplugin</groupId>
        <artifactId>actuator-maven-plugin</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <inherited>false</inherited>
        <configuration>
          <buildConfig>debug</buildConfig>
          <jsonFilePath>actuator/version.json</jsonFilePath>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
```
- Воспользоваться командой `actuator:appsign` для генерации `actuator/version.json`.

# Использование в рамках дочернего проекта webapp, отвечающего за сборку общего артефакта приложения с новой структурой (GWT + React + service-rest):
- Скорректировать `pom.xml` модуля `webapp` следующим образом:
```
      ...
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-war-plugin</artifactId>
        <version>3.2.2</version>
        <configuration>
          <webResources>
            <!-- build:gwt -->
            <resource>
              <directory>${project.build.directory}/temp-war-gwt</directory>
              <excludes>
                <exclude>WEB-INF/web.xml</exclude>
                <exclude>actuator/*</exclude>
              </excludes>
            </resource>
            
            ...
            
            <!-- actuator -->
            <resource>
              <directory>${project.build.directory}/actuator</directory>
              <targetPath>actuator</targetPath>
            </resource>
          </webResources>
          ...
        </configuration>
      </plugin>
      ...
      <plugin>
        <groupId>org.jepria.tools.mavenplugin</groupId>
        <artifactId>actuator-maven-plugin</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <configuration>
          <buildConfig>debug</buildConfig>
          <jsonFilePath>${project.build.directory}/actuator/version.json</jsonFilePath>
        </configuration>
        <executions>
          <execution>
            <id>generate-version-info-json</id>
            <phase>prepare-package</phase>
            <goals>
              <goal>appsign</goal>
            </goals>
          </execution>
        </executions>
      </plugin>
      ...
```


- Воспользоваться командой `actuator:appsign` от `pom.xml` модуля `webapp` для генерации `webapp/target/actuator/version.json`.
- ИЛИ воспользоваться командой `mvn clean package` от родительского `pom.xml`.
- ИЛИ воспользоваться командой `mvn clean package` от `pom.xml` модуля `webapp` (предварительно должны быть собраны модули `client-react`, `gwt` и `service-rest`).

# Параметры плагина `actuator-maven-plugin`:
- `buildConfig` -- тип сборки (обязательный);
- `libraryVersion` -- список используемых библиотек (не обязательный);
- `projectName` -- наименование общего проекта в SVN, например, "Jep" (не обязательный);
- `moduleName` -- наименование приложения, например, "JepRiaShowcase" (не обязательный);
- `jsonFilePath` -- путь до файла `version.json` (не обязательный, дефолтное значение: `${project.build.directory}/actuator/version.json`).

