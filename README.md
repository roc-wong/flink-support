## Load properties use for reference of springboot  

### 1. 在pom.xml中增加classpath:./config

```text
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-jar-plugin</artifactId>
    <version>3.0.2</version>
    <configuration>
        <archive>
            <manifest>
                <!--添加Class-Path依赖-->
                <addClasspath>true</addClasspath>
                <!--指定依赖包所在目录，不指定的话默认是在与当前jar包相同路径下-->
                <!--<classpathPrefix>lib/</classpathPrefix>-->
                <!--去除依赖jar包时的时间戳-->
                <useUniqueVersions>false</useUniqueVersions>
            </manifest>
            <manifestEntries>
                <Class-Path>config/</Class-Path>
            </manifestEntries>
        </archive>
    </configuration>
</plugin>
```

### 2. 使用maven-assembly-plugin将config目录放置于flink fatjar同级目录

assembly-descriptor.xml

```text
<assembly xmlns="http://maven.apache.org/ASSEMBLY/2.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/ASSEMBLY/2.0.0 http://maven.apache.org/xsd/assembly-2.0.0.xsd">
    <id>assembly</id>
    <formats>
        <format>zip</format>
    </formats>
    <includeBaseDirectory>false</includeBaseDirectory>
    <fileSets>
        <fileSet>
            <directory>${basedir}/src/main/config</directory>
            <outputDirectory>./config</outputDirectory>
            <includes>
                <include>*.properties</include>
                <include>*.yml</include>
            </includes>
            <lineEnding>unix</lineEnding>
        </fileSet>
        <fileSet>
            <directory>target</directory>
            <outputDirectory>./</outputDirectory>
            <includes>
                <include>${project.artifactId}-${project.version}.jar</include>
            </includes>
            <fileMode>0755</fileMode>
        </fileSet>
    </fileSets>
</assembly>
```

pom.xml

```text
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-assembly-plugin</artifactId>
    <version>2.6</version>
    <executions>
        <execution>
            <phase>package</phase>
            <goals>
                <goal>single</goal>
            </goals>
            <configuration>
                <finalName>${project.artifactId}-${project.version}</finalName>
                <appendAssemblyId>false</appendAssemblyId>
                <descriptors>
                    <descriptor>src/assembly/assembly-descriptor.xml</descriptor>
                </descriptors>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### 3. 在项目中使用ParameterToolPlus加载配置文件

```text
ParameterToolPlus parameterToolPlus = ParameterToolPlus.loadProperties(args);
String location = parameterToolPlus.getProperty("my.location");
```

## Flink 项目完整pom.xml

```text
<build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-jar-plugin</artifactId>
                <version>3.0.2</version>
                <configuration>
                    <archive>
                        <manifest>
                            <!--添加Class-Path依赖-->
                            <addClasspath>true</addClasspath>
                            <!--指定依赖包所在目录，不指定的话默认是在与当前jar包相同路径下-->
                            <!--<classpathPrefix>lib/</classpathPrefix>-->
                            <!--去除依赖jar包时的时间戳-->
                            <useUniqueVersions>false</useUniqueVersions>
                        </manifest>
                        <manifestEntries>
                            <Class-Path>config/</Class-Path>
                        </manifestEntries>
                    </archive>
                </configuration>
            </plugin>
            <!-- We use the maven-shade plugin to create a fat jar that contains all necessary dependencies. -->
            <!-- Change the value of <mainClass>...</mainClass> if your program entry point changes. -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <version>3.0.0</version>
                <executions>
                    <!-- Run shade goal on package phase -->
                    <execution>
                        <phase>package</phase>
                        <goals>
                            <goal>shade</goal>
                        </goals>
                        <configuration>
                            <artifactSet>
                                <excludes>
                                    <exclude>org.apache.flink:force-shading</exclude>
                                    <exclude>com.google.code.findbugs:jsr305</exclude>
                                    <exclude>org.slf4j:*</exclude>
                                    <exclude>log4j:*</exclude>
                                </excludes>
                            </artifactSet>
                            <filters>
                                <filter>
                                    <!-- Do not copy the signatures in the META-INF folder.
                                    Otherwise, this might cause SecurityExceptions when using the JAR. -->
                                    <artifact>*:*</artifact>
                                    <excludes>
                                        <exclude>META-INF/*.SF</exclude>
                                        <exclude>META-INF/*.DSA</exclude>
                                        <exclude>META-INF/*.RSA</exclude>
                                    </excludes>
                                </filter>
                            </filters>
                            <transformers>
                                <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                                    <mainClass>com.zts.ssm.nginxlog.etl.engine.APMEngineApplication</mainClass>
                                </transformer>
                            </transformers>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-assembly-plugin</artifactId>
                <version>2.6</version>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals>
                            <goal>single</goal>
                        </goals>
                        <configuration>
                            <finalName>${project.artifactId}-${project.version}</finalName>
                            <appendAssemblyId>false</appendAssemblyId>
                            <descriptors>
                                <descriptor>src/assembly/assembly-descriptor.xml</descriptor>
                            </descriptors>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
```
