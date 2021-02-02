package org.jepria.tools.mavenplugin.actuator;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;

import org.jepria.tools.mavenplugin.actuator.util.PluginUtil;
import org.jepria.tools.mavenplugin.actuator.version.Version;
import org.jepria.tools.mavenplugin.actuator.util.Json;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Scanner;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static org.jepria.tools.mavenplugin.actuator.util.PluginConstant.UTF_8;
import static org.jepria.tools.mavenplugin.actuator.util.PluginUtil.checkParameter;

/**
 * Goal which collects information about the assembly of the application.
 *
 */
@Mojo( name = "appsign", defaultPhase = LifecyclePhase.PREPARE_PACKAGE, requiresDependencyResolution = ResolutionScope.TEST )
public class AppSignMojo extends AbstractMojo
{
  
  /**
   * Plugin configuration to use in the execution.
   */
  @Parameter
  private XmlPlexusConfiguration configuration;

  /**
   * Mojo input parameter, build configuration.
   */
  @Parameter( property = "buildConfig", required = true )
  private String buildConfig;

  /**
   * Mojo input parameter, string with versions of used libraries (comma separated).
   */
  @Parameter( defaultValue = "", property = "libraryVersion" )
  private String libraryVersion;

  /**
   * Mojo input parameter, repository name, e.g. "Jep".
   */
  @Parameter( defaultValue = "", property = "projectName" )
  private String projectName;

  /**
   * Mojo input parameter, module name, e.g. "JepRiaShowcase".
   */
  @Parameter( defaultValue = "", property = "moduleName" )
  private String moduleName;

  /**
   * Mojo input parameter, path to *.json file with application version information.
   */
  @Parameter( defaultValue = "${project.build.directory}/actuator/version.json", property = "jsonFilePath" )
  private String jsonFilePath;

  /**
   * The project currently being build.
   */
  @Parameter( defaultValue = "${project}", readonly = true )
  private MavenProject mavenProject;

  /**
   * The current Maven session.
   */
  @Parameter( defaultValue = "${session}", readonly = true )
  private MavenSession mavenSession;

  /**
   * The Maven BuildPluginManager component.
   */
  @Component
  private BuildPluginManager pluginManager;

  /**
   * Auxiliary parameters.
   */
  private String hostName, userName, svnVersionInfo, svnTag, buildUUID;

  public void execute() throws MojoExecutionException
  {
    getLog().info("Generating a JSON file with information about the assembly...");
    checkParameter(buildConfig, "Incorrect parameter: buildConfig!");
    if (PluginUtil.isEmpty(libraryVersion)) {
      extractlibraryVersion();
    }
    extractSvnVersionInfo();
    getLog().info("Try to generate application info into JSON...");
    Version appVersion = createVersion();
    Json json = Json.object()
      .set("library", Json.make(appVersion.getLibrary()))
      .set("compile", Json.make(appVersion.getCompile()))
      .set("svn", Json.make(appVersion.getSvn()));
    saveToJSONFile(json.toString());
  }

  private Version createVersion() throws MojoExecutionException {
    Version version = new Version();
    try {
      if (!PluginUtil.isEmpty(libraryVersion)) {
        // список библиотек через запятую: JepRia:10.1.0, GWT:6.3.0
        String[] libVersionPair = libraryVersion.split(",");
        for (String lib : libVersionPair) {
          // название библиотеки версия через :
          String[] t = lib.split(":");
          if (t.length < 2) {
            version.addLibInf("Jepria",t[0]);// не указано название библиотеки только версия, JepRIA?
          } else version.addLibInf(t[0],t[1]);
        }
      }
    } catch (PatternSyntaxException e) {
      throw new MojoExecutionException(e.getLocalizedMessage(), e);
    }

    buildUUID = UUID.randomUUID().toString();
    
    version.addCompileInf("UUID", buildUUID);
    version.addCompileInf("time_stamp", new SimpleDateFormat("yyyy.MM.dd HH:mm:ss").format(new Date()));
    if (buildConfig.equals("debug")) {
      version.addCompileInf("host_name", getHostName());
      version.addCompileInf("user_name", getUserName());
    }

    version.addSvnInf("repo_name", projectName);
    version.addSvnInf("module_name", moduleName);
    version.addSvnInf("revision", svnVersionInfo);
    version.addSvnInf("tag_version", svnTag);

    return version;
  }

  private String commandExecution(String... command) throws IOException {
    BufferedReader reader = null;
    StringBuilder sb = new StringBuilder();
    try {
      ProcessBuilder builder = new ProcessBuilder(command);
      builder.redirectErrorStream(true);
      Process process = builder.start();
      InputStream is = process.getInputStream();
      reader = new BufferedReader(new InputStreamReader(is));
      String line = null;
      while ((line = reader.readLine()) != null) {
        sb.append(line);
      }
    } finally {
        if (reader != null) reader.close();
    }
    return sb.toString();
  }

  private void extractSvnVersionInfo() throws MojoExecutionException {
    getLog().info("Extracting SVN information...");
    String lineEnd = System.getProperty("line.separator");
    String svnVersionRaw = "";
    try {
      svnVersionRaw = commandExecution("svnversion");
    } catch (Exception e) {
        throw new MojoExecutionException("Error executing command \"svnversion\"!"+lineEnd+e.getMessage(), e);
    }
    svnVersionInfo = PluginUtil.extractSubStrByPattern(svnVersionRaw, "^(\\d+\\D*:?\\d+\\D*)$", 1, "");
    getLog().info("SVN Version Info: " + svnVersionInfo);
    String svnPathRaw = "";
    try {
      svnPathRaw = commandExecution("svn", "info", "--xml");
    } catch (Exception e) {
        throw new MojoExecutionException("Error executing command \"svn info --xml\"!"+lineEnd+e.getMessage(), e);
    }
    String svnPath = PluginUtil.extractSubStrByPattern(svnPathRaw, "(?<=<url>).*?(?=</url>)", 0, "");
    svnTag = PluginUtil.extractSubStrByPattern(svnPath, "/Tag/([\\d]+([\\.][\\d]+)*((-|_)[\\w]*)?)", 1, "");
    moduleName = !PluginUtil.isEmpty(moduleName) ? moduleName : PluginUtil.extractSubStrByPattern(svnPath, "/Module/([^/]+)", 1, "");
    projectName = !PluginUtil.isEmpty(projectName) ? projectName : PluginUtil.extractSubStrByPattern(svnPath, "/([^/]+)(/svn)?/Module", 1, "");
  }

  public String getHostName() {
    if (PluginUtil.isEmpty(hostName)) {
      String os = System.getProperty("os.name").toLowerCase();
      if (os.contains("win")) {
        hostName = System.getenv("COMPUTERNAME");
      } else if (os.contains("nix") || os.contains("nux") || os.contains("mac os x")) {
        hostName = System.getenv("HOSTNAME");
      }      
    }
    return hostName;
  }

  public String getUserName() {
    if (PluginUtil.isEmpty(userName)) {
      userName = System.getProperty("user.name");
    }
    return userName;
  }

  private void saveToJSONFile(String jsonStr) throws MojoExecutionException {
    File jsonFile = new File(jsonFilePath).getAbsoluteFile();
    getLog().info("Try to save information into " + jsonFile.getName() + "...");
    File dir = jsonFile.getParentFile();
    if (!dir.exists()) {
      dir.mkdirs();
    }
    FileWriter w = null;
    try {
      w = new FileWriter(jsonFile, false);
      w.write(jsonStr);
    } catch (IOException e) {
      throw new MojoExecutionException( "Error creating file " + jsonFile, e);
    } finally {
      if (w != null) {
        try {
          w.close();
        } catch (IOException e) {
          // ignore
        }
      }
    }
    getLog().info("Successfully saved to file " + jsonFile + ".");
  }

  private String extractJepRiaVersion(File depProp, String regexpPattern, int groupIndex) throws FileNotFoundException {
    String resultStr = "";
    Scanner br = new Scanner(depProp, UTF_8);
    try {
      while (br.hasNextLine()) {
        resultStr = PluginUtil.extractSubStrByPattern(br.nextLine(), regexpPattern, groupIndex, "").trim();
        if (resultStr.length() > 0) {
          break;
        }
      }
    } finally {
      br.close();
    }
    return resultStr;
  }

  private String extractJFrontVersion(File depProp) throws FileNotFoundException {
    StringBuilder sb = new StringBuilder();
    Scanner br = new Scanner(depProp, UTF_8);
    try {
      Pattern p = Pattern.compile("\"(@jfront/[^\"]+)\"[\\s]*:[\\s]*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
      while (br.hasNextLine()) {
        Matcher m = p.matcher(br.nextLine());
        while (m.find() == true) {
          String left = m.group(1).trim();
          String right = m.group(2).trim();
          if (!PluginUtil.isEmpty(left) && !PluginUtil.isEmpty(right)) {
            if (sb.length() > 0) {
              sb.append(",");
            }
            sb.append(left + ":" + right);
          }
        }
      }
    } finally {
      br.close();
    }
    return sb.toString();
  }

  private void extractlibraryVersion() throws MojoExecutionException {
    // TODO Refactoring required!!!
    getLog().info("Extracting library version...");
    StringBuilder sb = new StringBuilder();
    File depProp = new File("dependency.properties").getAbsoluteFile();
    boolean isJepRiaOnly = true;
    if (!depProp.exists()) {
      depProp = Paths.get(Paths.get("").toAbsolutePath().getParent().toString(), "gwt", "dependency.properties").toFile();
      isJepRiaOnly = false;
    }  
    if (depProp.exists()) {
      String jepRiaVersion = "";
      try {
        jepRiaVersion = extractJepRiaVersion(depProp, "JEPRIA_VERSION[\\s]*=[\\s]*([\\d]+([\\.][\\d]+)*((-|_)[\\w]*)?)", 1);
      } catch(FileNotFoundException e) {
        // Unreachable statement
      }
      if (!PluginUtil.isEmpty(jepRiaVersion)) {
        sb.append("JepRia:" + jepRiaVersion);
      }
    }
    if (!isJepRiaOnly) {
      depProp = Paths.get(Paths.get("").toAbsolutePath().getParent().toString(), "service-rest", "pom.xml").toFile();
      if (depProp.exists()) {
        String jepRiaVersion = "";
        try {
          jepRiaVersion = extractJepRiaVersion(depProp, "(?<=<jepria.version>).*?(?=</jepria.version>)", 0);
        } catch(FileNotFoundException e) {
          // Unreachable statement
        }
        if (!PluginUtil.isEmpty(jepRiaVersion)) {
          if (sb.length() > 0) {
            sb.append(",jepria-rest:" + jepRiaVersion);
          } else {
            sb.append("jepria-rest:" + jepRiaVersion);
          }
        }
      }
      depProp = Paths.get(Paths.get("").toAbsolutePath().getParent().toString(), "client-react", "package.json").toFile();
      if (depProp.exists()) {
        String jFrontVersion = "";
        try {
          jFrontVersion = extractJFrontVersion(depProp);
        } catch(FileNotFoundException e) {
          // Unreachable statement
        }
        if (!PluginUtil.isEmpty(jFrontVersion)) {
          if (sb.length() > 0) {
            sb.append(",");
          }
          sb.append(jFrontVersion);
        }
      }
    }
    libraryVersion = sb.toString();
  }

}
