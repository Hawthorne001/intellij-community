/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * @author cdr
 */
package com.intellij.lang.properties.structureView;

import com.intellij.lang.properties.*;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import gnu.trove.THashMap;
import gnu.trove.TIntLongHashMap;
import gnu.trove.TIntProcedure;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

@State(
  name="PropertiesSeparatorManager",
  storages= {
    @Storage(
      file = StoragePathMacros.PROJECT_FILE
    )}
)
public class PropertiesSeparatorManager implements PersistentStateComponent<Element> {
  @NonNls private static final String FILE_ELEMENT = "file";
  @NonNls private static final String URL_ELEMENT = "url";
  @NonNls private static final String SEPARATOR_ATTR = "separator";
  private final Project myProject;

  public static PropertiesSeparatorManager getInstance(final Project project) {
    return ServiceManager.getService(project, PropertiesSeparatorManager.class);
  }

  private final Map<String, String> mySeparators = new THashMap<String, String>();

  public PropertiesSeparatorManager(final Project project) {
    myProject = project;
  }

  @NotNull
  public String getSeparator(final ResourceBundle resourceBundle) {
    if (!(resourceBundle instanceof ResourceBundleImpl)) {
      return ".";
    }
    String separator = mySeparators.get(((ResourceBundleImpl)resourceBundle).getUrl());
    if (separator == null) {
      separator = guessSeparator((ResourceBundleImpl)resourceBundle);
      setSeparator(resourceBundle, separator);
    }
    return separator;
  }

  //returns most probable separator in properties files
  private static String guessSeparator(final ResourceBundleImpl resourceBundle) {
    final TIntLongHashMap charCounts = new TIntLongHashMap();
    for (PropertiesFile propertiesFile : resourceBundle.getPropertiesFiles()) {
      if (propertiesFile == null) continue;
      List<IProperty> properties = propertiesFile.getProperties();
      for (IProperty property : properties) {
        String key = property.getUnescapedKey();
        if (key == null) continue;
        for (int i =0; i<key.length(); i++) {
          char c = key.charAt(i);
          if (!Character.isLetterOrDigit(c)) {
            charCounts.put(c, charCounts.get(c) + 1);
          }
        }
      }
    }

    final char[] mostProbableChar = new char[]{'.'};
    charCounts.forEachKey(new TIntProcedure() {
      long count = -1;
      public boolean execute(int ch) {
        long charCount = charCounts.get(ch);
        if (charCount > count) {
          count = charCount;
          mostProbableChar[0] = (char)ch;
        }
        return true;
      }
    });
    if (mostProbableChar[0] == 0) {
      mostProbableChar[0] = '.';
    }
    return Character.toString(mostProbableChar[0]);
  }

  public void setSeparator(ResourceBundle resourceBundle, String separator) {
    if (resourceBundle instanceof ResourceBundleImpl) {
      mySeparators.put(((ResourceBundleImpl)resourceBundle).getUrl(), separator);
    }
  }

  public void loadState(final Element element) {
    List<Element> files = element.getChildren(FILE_ELEMENT);
    for (Element fileElement : files) {
      String url = fileElement.getAttributeValue(URL_ELEMENT, "");
      String separator = fileElement.getAttributeValue(SEPARATOR_ATTR,"");
      separator = decodeSeparator(separator);
      if (separator == null) {
        continue;
      }
      ResourceBundle resourceBundle = PropertiesImplUtil.createByUrl(url, myProject);
      if (resourceBundle != null) {
        mySeparators.put(url, separator);
      }
    }
  }

  @Nullable
  private static String decodeSeparator(String separator) {
    if (separator.length() % 6 != 0) {
      return null;
    }
    StringBuilder result = new StringBuilder();
    int pos = 0;
    while (pos < separator.length()) {
      String encodedCharacter = separator.substring(pos, pos+6);
      if (!encodedCharacter.startsWith("\\u")) {
        return null;
      }
      int d1 = Character.digit(encodedCharacter.charAt(2), 16);      
      int d2 = Character.digit(encodedCharacter.charAt(3), 16);      
      int d3 = Character.digit(encodedCharacter.charAt(4), 16);      
      int d4 = Character.digit(encodedCharacter.charAt(5), 16);
      if (d1 == -1 || d2 == -1 || d3 == -1 || d4 == -1) {
        return null;
      }
      int b1 = (d1 << 12) & 0xF000;
      int b2 = (d2 << 8) & 0x0F00;
      int b3 = (d3 << 4) & 0x00F0;
      int b4 = (d4 << 0) & 0x000F;
      char code = (char) (b1 | b2 | b3 | b4);
      result.append(code);
      pos += 6;
    }
    return result.toString();
  }

  public Element getState() {
    Element element = new Element("PropertiesSeparatorManager");
    for (final String url: mySeparators.keySet()) {
      String separator = mySeparators.get(url);
      StringBuilder encoded = new StringBuilder(separator.length());
      for (int i=0;i<separator.length();i++) {
        char c = separator.charAt(i);
        encoded.append("\\u");
        encoded.append(Character.forDigit(c >> 12, 16));
        encoded.append(Character.forDigit((c >> 8) & 0xf, 16));
        encoded.append(Character.forDigit((c >> 4) & 0xf, 16));
        encoded.append(Character.forDigit(c & 0xf, 16));
      }
      Element fileElement = new Element(FILE_ELEMENT);
      fileElement.setAttribute(URL_ELEMENT, url);
      fileElement.setAttribute(SEPARATOR_ATTR, encoded.toString());
      element.addContent(fileElement);
    }
    return element;
  }
}
