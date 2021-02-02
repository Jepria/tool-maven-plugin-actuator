package org.jepria.tools.mavenplugin.actuator.util;

import static org.jepria.tools.mavenplugin.actuator.util.PluginConstant.UNDEFINED_INT;
import static org.jepria.tools.mavenplugin.actuator.util.PluginConstant.DEFAULT_HTTP_PORT;
import static org.jepria.tools.mavenplugin.actuator.util.PluginConstant.HTTP_PROTOCOL;
import static org.jepria.tools.mavenplugin.actuator.util.PluginConstant.UTF_8;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.maven.plugin.MojoExecutionException;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

@SuppressWarnings("unchecked")
public final class PluginUtil {

  private static final String WIN_CHARSET = "windows-1251";

  /**
   * Извлечение подстроки, соответствующей шаблону и индексу группы
   * 
   * @param strings строка для поиска
   * @param strings шаблон регулярного выражения
   * @param integer индекс группы
   * @param strings значение по умолчанию
   * @return найденная подстрока или значение по умолчанию
   */
  public static String extractSubStrByPattern(String inputStr, String regexpPattern, int groupIndex, String defaultValue) {
    Pattern p = Pattern.compile(regexpPattern, Pattern.CASE_INSENSITIVE);
    Matcher m = p.matcher(inputStr);
    return (m.find() == true ) ? m.group(groupIndex) : defaultValue;
  }

  /**
   * Множественное объединение строк
   * 
   * @param strings объединяемые строки
   * @return единая строка
   */
  public static String multipleConcat(String... strings) {
    StringBuilder strBuilder = new StringBuilder(strings.length);
    for (String str : strings) {
      strBuilder.append(str);
    }
    return strBuilder.toString();
  }

  /**
   * Формирование URL вида <протокол>://<сервер>:<порт>
   * 
   * @param protocol   протокол
   * @param serverName сервер
   * @param serverPort порт
   * @return строка вида <протокол>://<сервер>:<порт>
   * @throws Exception
   */
  public static String prepareServerUrl(String protocol, String serverName,
      String serverPort) throws Exception {

    if (isEmptyOrNotInitializedParameter(serverName))
      throw new Exception("Server name must be declared!");
    if (isEmpty(protocol))
      protocol = HTTP_PROTOCOL;
    if (isEmptyOrNotInitializedParameter(serverPort))
      serverPort = DEFAULT_HTTP_PORT;

    return multipleConcat(protocol, "://", serverName, ":", serverPort);
  }

  /**
   * Функция определяет: является ли переданная ей строка пустой.
   * 
   * @param sourceString исходная строка, которую проверяем
   * 
   * @return возвращает true, если передано значение null или переданная
   *         строка состоит только из пробелов.
   */
  public static boolean isEmpty(String sourceString) {
    return sourceString == null || sourceString.trim().length() == 0;
  }
  
  /**
   * Функция определяет: является ли переданное ей число пустым или неопределенным.
   * 
   * @param sourceInteger
   *            исходное число, которое проверяем
   * 
   * @return возвращает true, если передано значение null или переданное
   *         значение не определено
   */
  public static boolean isEmpty(Integer sourceInteger) {
    return sourceInteger == null || sourceInteger.intValue() == UNDEFINED_INT;
  }
  
  /**
   * Функция определяет: является ли переданное ей число пустым или неопределенным.
   * 
   * @param sourceList
   *            исходное число, которое проверяем
   * 
   * @return возвращает true, если передано значение null или переданное
   *         значение не определено
   */
  public static boolean isEmpty(List<?> sourceList) {
    return sourceList == null || sourceList.isEmpty();
  }

  /**
   * Функция определяет: является ли переданный объект пустым.
   * 
   * @param obj   проверяемый объект
   */
  public static boolean isEmpty(Object obj) {
    if (obj == null) {
      return true;
    } else if (obj instanceof String) {
      return isEmpty((String) obj);
    } else if (obj instanceof Integer) {
      return isEmpty((Integer) obj);
    }
    else if (obj instanceof Integer) {
      return isEmpty((Integer) obj);
    }
    else if (obj instanceof List) {
      return isEmpty((List<?>) obj);
    }
    return false;
  }

  /**
   * Функция определяет: является ли переданная ей пустой строкой или
   * непроинициализированным параметром комманды.
   * 
   * @param sourceString   исходная строка, которую проверяем
   * 
   * @return возвращает true, если передано значение null или переданная
   *         строка состоит только из пробелов, а также строка является
   *         параметром Ant. Пример, ${PORT}, ${MODULE_NAME} и т.д.
   */
  public static boolean isEmptyOrNotInitializedParameter(String sourceString) {
    return isEmpty(sourceString) ? true : isNotInitializedParameter(sourceString);
  }
  
  public static boolean isNotInitializedParameter(String sourceString) {
    return !isEmpty(sourceString) && sourceString.startsWith("${") && sourceString.endsWith("}");
  }

  /**
   * Кодирование строки
   * 
   * @param inputString      кодируемая строка
   * @return кодированная строка
   * @throws UnsupportedEncodingException
   */
  public static String encode(String inputString)
    throws UnsupportedEncodingException {
    inputString = URLEncoder.encode(inputString, UTF_8);
    char[] chars = inputString.toCharArray();
    StringBuffer hex = new StringBuffer();
    for (int i = 0; i < chars.length; i++) {
      hex.append(Integer.toHexString((int) chars[i]));
    }
    return hex.toString();
  }

  /**
   * Декодирование строки
   * 
   * @param decodeString      декодируемая строка
   * @return раскодированная строка
   * @throws UnsupportedEncodingException
   */
  public static String decode(String decodeString) 
    throws UnsupportedEncodingException {
    StringBuilder sb = new StringBuilder();
    StringBuilder temp = new StringBuilder();
    // 49204c6f7665204a617661 split into two characters 49, 20, 4c...
    for (int i = 0; i < decodeString.length() - 1; i += 2) {
      // grab the hex in pairs
      String output = decodeString.substring(i, (i + 2));
      // convert hex to decimal
      int decimal = Integer.parseInt(output, 16);
      // convert the decimal to character
      sb.append((char) decimal);
      temp.append(decimal);
    }
    return URLDecoder.decode(sb.toString(), UTF_8);
  }

  public static void checkParameter(String paramName, String errorMessage) throws MojoExecutionException {
    if (isEmptyOrNotInitializedParameter(paramName)) {
      throw new MojoExecutionException(errorMessage);
    }
  }

  /**
   * Сравнение объектов на равенство.
   * 
   * @param obj1      первый сравниваемый объект
   * @param obj2      второй сравниваемый объект
   * @return признак равенства сравниваемых объектов
   */
  public static boolean equalWithNull(Object obj1, Object obj2) {
    if (obj1 == obj2) {
      return true;
    } else if (obj1 == null) {
      return false;
    } else {
      return obj1.equals(obj2);
    }
  }

  /**
   * Получение структуры документа
   * 
   * @param fileNameOrPath наименование файла настроек или пути для генерации структуры
   * @return документ
   * @throws IOException
   * @throws SAXException
   * @throws ParserConfigurationException
   */
  public static final Document getDOM(String fileNameOrPath)
      throws IOException, SAXException, ParserConfigurationException {
    DocumentBuilder db = createDocumentBuilder();
    Document doc = db.parse(fileNameOrPath);
    return doc;
  }

  /**
   * Создание билдера для построения DOM-структуры
   * 
   * @return билдер
   * @throws ParserConfigurationException
   */
  public static final DocumentBuilder createDocumentBuilder()
      throws ParserConfigurationException {
    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    dbf.setNamespaceAware(true); // never forget this!
    dbf.setIgnoringElementContentWhitespace(true);
    DocumentBuilder db = dbf.newDocumentBuilder();
    return db;
  }

}
