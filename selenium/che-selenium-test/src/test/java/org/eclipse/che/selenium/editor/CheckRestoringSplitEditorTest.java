/*
 * Copyright (c) 2012-2018 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.selenium.editor;

import static org.eclipse.che.selenium.core.TestGroup.FLAKY;
import static org.eclipse.che.selenium.core.constant.TestTimeoutsConstants.LOAD_PAGE_TIMEOUT_SEC;
import static org.eclipse.che.selenium.core.constant.TestTimeoutsConstants.WIDGET_TIMEOUT_SEC;
import static org.eclipse.che.selenium.core.project.ProjectTemplates.MAVEN_JAVA_MULTIMODULE;
import static org.eclipse.che.selenium.pageobject.CodenvyEditor.TabActionLocator.SPIT_HORISONTALLY;
import static org.eclipse.che.selenium.pageobject.CodenvyEditor.TabActionLocator.SPLIT_VERTICALLY;
import static org.testng.Assert.fail;

import com.google.common.base.Joiner;
import com.google.inject.Inject;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import org.eclipse.che.commons.lang.NameGenerator;
import org.eclipse.che.commons.lang.Pair;
import org.eclipse.che.selenium.core.SeleniumWebDriver;
import org.eclipse.che.selenium.core.client.TestProjectServiceClient;
import org.eclipse.che.selenium.core.workspace.TestWorkspace;
import org.eclipse.che.selenium.pageobject.CodenvyEditor;
import org.eclipse.che.selenium.pageobject.Ide;
import org.eclipse.che.selenium.pageobject.Loader;
import org.eclipse.che.selenium.pageobject.NotificationsPopupPanel;
import org.eclipse.che.selenium.pageobject.PopupDialogsBrowser;
import org.eclipse.che.selenium.pageobject.ProjectExplorer;
import org.openqa.selenium.TimeoutException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/** @author Musienko Maxim */
@Test(groups = FLAKY)
public class CheckRestoringSplitEditorTest {
  private String javaClassName = "AppController.java";
  private String readmeFileName = "README.md";
  private String pomFileTab = "qa-spring-sample";
  private String javaClassTab = "AppController";
  private final String PROJECT_NAME = NameGenerator.generate("project", 4);;
  private final String PATH_TO_JAVA_FILE =
      PROJECT_NAME + "/src/main/java/org/eclipse/qa/examples/" + javaClassName;
  private Pair<Integer, Integer> cursorPositionForJavaFile = new Pair<>(13, 1);
  private Pair<Integer, Integer> cursorPositionForReadMeFile = new Pair<>(1, 10);
  private Pair<Integer, Integer> cursorPositionForPomFile = new Pair<>(32, 1);
  private List<String> expectedTextFromEditor;

  @Inject private TestWorkspace workspace;
  @Inject private Ide ide;
  @Inject private ProjectExplorer projectExplorer;
  @Inject private Loader loader;
  @Inject private CodenvyEditor editor;
  @Inject private PopupDialogsBrowser popupDialogsBrowser;
  @Inject private TestProjectServiceClient testProjectServiceClient;
  @Inject private SeleniumWebDriver seleniumWebDriver;
  @Inject private NotificationsPopupPanel notificationsPopupPanel;

  @BeforeClass
  public void prepare() throws Exception {
    String splitter = "----split_line---";
    URL resources =
        CheckRestoringSplitEditorTest.class.getResource("split-editor-restore-exp-text.txt");
    expectedTextFromEditor =
        Files.readAllLines(Paths.get(resources.toURI()), Charset.forName("UTF8"));
    String expectedTextFromFile = Joiner.on("\n").join(expectedTextFromEditor);
    expectedTextFromEditor = Arrays.asList(expectedTextFromFile.split(splitter));
    URL resource = getClass().getResource("/projects/default-spring-project");
    testProjectServiceClient.importProject(
        workspace.getId(), Paths.get(resource.toURI()), PROJECT_NAME, MAVEN_JAVA_MULTIMODULE);
    ide.open(workspace);
  }

  @Test
  public void checkRestoringStateSplitEditor() throws IOException, Exception {
    projectExplorer.waitItem(PROJECT_NAME);
    projectExplorer.quickExpandWithJavaScript();
    splitEditorAndOpenFiles();
    setPositionsForSplitEditor();
    editor.waitActive();
    if (popupDialogsBrowser.isAlertPresent()) {
      popupDialogsBrowser.acceptAlert();
    }

    projectExplorer.waitAndSelectItem(PROJECT_NAME);
    projectExplorer.waitItemIsSelected(PROJECT_NAME);

    seleniumWebDriver.navigate().refresh();
    projectExplorer.waitItem(PROJECT_NAME);
    loader.waitOnClosed();
    projectExplorer.waitVisibilityByName(javaClassName, WIDGET_TIMEOUT_SEC);

    notificationsPopupPanel.waitPopupPanelsAreClosed();
    checkSplitEditorAfterRefreshing(
        1, javaClassTab, expectedTextFromEditor.get(0), cursorPositionForJavaFile);
    checkSplitEditorAfterRefreshing(
        2, readmeFileName, expectedTextFromEditor.get(1).trim(), cursorPositionForReadMeFile);
    checkSplitEditorAfterRefreshing(
        3, pomFileTab, expectedTextFromEditor.get(2).trim(), cursorPositionForPomFile);
  }

  private void checkSplitEditorAfterRefreshing(
      int numOfEditor,
      String nameOfEditorTab,
      String expectedTextAfterRefresh,
      Pair<Integer, Integer> pair)
      throws IOException {

    editor.waitActive();
    editor.selectTabByName(nameOfEditorTab);
    editor.waitCursorPosition(numOfEditor - 1, pair.first, pair.second);

    try {
      editor.waitTextInDefinedSplitEditor(
          numOfEditor, LOAD_PAGE_TIMEOUT_SEC, expectedTextAfterRefresh);
    } catch (TimeoutException ex) {
      // remove try-catch block after issue has been resolved
      fail("Known random failure https://github.com/eclipse/che/issues/9456", ex);
    }
  }

  private void splitEditorAndOpenFiles() {
    String namePomFile = "pom.xml";

    projectExplorer.openItemByPath(PATH_TO_JAVA_FILE);
    loader.waitOnClosed();
    editor.waitActive();
    editor.openAndWaitContextMenuForTabByName(javaClassTab);
    editor.runActionForTabFromContextMenu(SPIT_HORISONTALLY);
    editor.selectTabByIndexEditorWindowAndOpenMenu(0, javaClassTab);
    editor.runActionForTabFromContextMenu(SPLIT_VERTICALLY);
    loader.waitOnClosed();
    editor.selectTabByIndexEditorWindow(1, javaClassTab);
    projectExplorer.openItemByPath(PROJECT_NAME + "/" + readmeFileName);
    editor.selectTabByIndexEditorWindow(2, javaClassTab);
    projectExplorer.openItemByPath(PROJECT_NAME + "/" + namePomFile);
  }

  private void setPositionsForSplitEditor() {
    editor.selectTabByIndexEditorWindow(0, javaClassTab);
    editor.goToPosition(0, cursorPositionForJavaFile.first, cursorPositionForJavaFile.second);
    editor.selectTabByName(readmeFileName);
    editor.goToPosition(1, cursorPositionForReadMeFile.first, cursorPositionForReadMeFile.second);
    editor.selectTabByName(pomFileTab);
    editor.goToPosition(2, cursorPositionForPomFile.first, cursorPositionForPomFile.second);
  }
}
