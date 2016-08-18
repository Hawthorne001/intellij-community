package org.jetbrains.debugger.memory.view;

import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.impl.XSourcePositionImpl;
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl;
import com.intellij.xdebugger.impl.ui.XDebuggerExpressionEditor;
import com.sun.jdi.ReferenceType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

class ExpressionEditorWithHistory extends XDebuggerExpressionEditor {
  private static final String HISTORY_ID_PREFIX = "filtering";

  ExpressionEditorWithHistory(final @NotNull Project project,
                              final @NotNull ReferenceType referenceType,
                              final @NotNull XDebuggerEditorsProvider debuggerEditorsProvider,
                              final @Nullable Disposable parentDisposable) {
    super(project, debuggerEditorsProvider, HISTORY_ID_PREFIX + referenceType.name(), null,
        XExpressionImpl.EMPTY_EXPRESSION, false, true, true);



    new AnAction("InstancesWindow.ShowHistory") {
      @Override
      public void actionPerformed(AnActionEvent e) {
        showHistory();
      }

      @Override
      public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(LookupManager.getActiveLookup(getEditor()) == null);
      }
    }.registerCustomShortcutSet(CustomShortcutSet.fromString("DOWN"), getComponent(), parentDisposable);

    new SwingWorker<Void, Void>() {
      @Override
      protected Void doInBackground() throws Exception {
        ApplicationManager.getApplication().runReadAction(() -> {
          final PsiClass psiClass = DebuggerUtils.findClass(referenceType.name(),
              project, GlobalSearchScope.allScope(project));
          XSourcePositionImpl position = XSourcePositionImpl.createByElement(psiClass);
          SwingUtilities.invokeLater(() -> setSourcePosition(position));
        });
        return null;
      }
    }.execute();
  }

  private void showHistory() {
    List<XExpression> expressions = getRecentExpressions();
    if (!expressions.isEmpty()) {
      ListPopupImpl historyPopup = new ListPopupImpl(new BaseListPopupStep<XExpression>(null, expressions) {
        @Override
        public PopupStep onChosen(XExpression selectedValue, boolean finalChoice) {
          setExpression(selectedValue);
          requestFocusInEditor();
          return FINAL_CHOICE;
        }
      }) {
        @Override
        protected ListCellRenderer getListElementRenderer() {
          return new ColoredListCellRenderer<XExpression>() {
            @Override
            protected void customizeCellRenderer(@NotNull JList list, XExpression value, int index,
                                                 boolean selected, boolean hasFocus) {
              append(value.getExpression());
            }
          };
        }
      };

      historyPopup.getList().setFont(EditorUtil.getEditorFont());
      historyPopup.showUnderneathOf(getEditorComponent());
    }
  }
}
