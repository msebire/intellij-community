/*
 * User: anna
 * Date: 15-Apr-2009
 */
package com.intellij.codeInspection.ex;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.packageDependencies.DefaultScopesProvider;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.profile.ProfileManager;
import com.intellij.profile.codeInspection.SeverityProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.PackageSet;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ToolsImpl implements Tools {
  private static final Logger LOG = Logger.getInstance("#" + ToolsImpl.class.getName());
  public static final String ENABLED_TAG = "enabled_by_default";

  private String myShortName;
  private ScopeToolState myDefaultState;
  private List<ScopeToolState> myTools;
  private boolean myEnabled;

  public ToolsImpl(@NotNull InspectionProfileEntry tool, HighlightDisplayLevel level, boolean enabled) {
    myShortName = tool.getShortName();
    myEnabled = enabled;
    myDefaultState = new ScopeToolState(DefaultScopesProvider.getAllScope(), tool, enabled, level);
  }


  public void addTool(NamedScope scope, @NotNull InspectionProfileEntry tool, boolean enabled, HighlightDisplayLevel level) {
    if (myTools == null) {
      myTools = new ArrayList<ScopeToolState>();
    }
    myTools.add(new ScopeToolState(scope, tool, enabled, level));
  }

  public InspectionProfileEntry getInspectionTool(PsiElement element) {
    if (myTools != null) {
      for (ScopeToolState state : myTools) {
        if (element == null) {
          return state.getTool();
        }
        else {
          final DependencyValidationManager validationManager = DependencyValidationManager.getInstance(element.getProject());
          if (state.getScope() != null && state.getScope().getValue().contains(element.getContainingFile(), validationManager)) {
            return state.getTool();
          }
        }
      }

      for (ScopeToolState state : getTools()) {
        if (state.getScope() == null) {
          return state.getTool();
        }
      }
    }
    return myDefaultState.getTool();
  }

  public String getShortName() {
    return myShortName;
  }

  public List<InspectionProfileEntry> getAllTools() {
    final List<InspectionProfileEntry> result = new ArrayList<InspectionProfileEntry>();
    for (ScopeToolState state : getTools()) {
      result.add(state.getTool());
    }
    return result;
  }

  public void writeExternal(Element inspectionElement) throws WriteExternalException {
    if (myTools != null) {
      for (ScopeToolState state : myTools) {
        final NamedScope namedScope = state.getScope();
        final Element scopeElement = new Element("scope");
        scopeElement.setAttribute("name", namedScope.getName());
        scopeElement.setAttribute(InspectionProfileImpl.LEVEL_TAG, state.getLevel().toString());
        scopeElement.setAttribute(InspectionProfileImpl.ENABLED_TAG, Boolean.toString(state.isEnabled()));
        InspectionProfileEntry InspectionProfileEntry = state.getTool();
        InspectionProfileEntry.writeSettings(scopeElement);
        inspectionElement.addContent(scopeElement);
      }
    }
    inspectionElement.setAttribute(InspectionProfileImpl.ENABLED_TAG, Boolean.toString(isEnabled()));
    inspectionElement.setAttribute(InspectionProfileImpl.LEVEL_TAG, getLevel().toString());
    inspectionElement.setAttribute(ENABLED_TAG, Boolean.toString(myDefaultState.isEnabled()));
    myDefaultState.getTool().writeSettings(inspectionElement);
  }

  public void readExternal(Element toolElement, InspectionProfile profile) throws InvalidDataException {
    final String levelName = toolElement.getAttributeValue(InspectionProfileImpl.LEVEL_TAG);
    final ProfileManager profileManager = profile.getProfileManager();
    HighlightDisplayLevel level =
      HighlightDisplayLevel.find(((SeverityProvider)profileManager).getOwnSeverityRegistrar().getSeverity(levelName));
    if (level == null || level == HighlightDisplayLevel.DO_NOT_SHOW) {//from old profiles
      level = HighlightDisplayLevel.WARNING;
    }
    myDefaultState.setLevel(level);
    final String enabled = toolElement.getAttributeValue(InspectionProfileImpl.ENABLED_TAG);
    final boolean isEnabled = enabled != null && Boolean.parseBoolean(enabled);
    myEnabled = isEnabled;

    final String enabledTool = toolElement.getAttributeValue(ENABLED_TAG);
    myDefaultState.setEnabled(enabledTool != null ? Boolean.parseBoolean(enabledTool) : isEnabled);
    final InspectionProfileEntry tool = myDefaultState.getTool();
    tool.readSettings(toolElement);
    final List children = toolElement.getChildren(InspectionProfileImpl.SCOPE);
    if (!children.isEmpty()) {
      for (Object sO : children) {
        final Element scopeElement = (Element)sO;
        final String scopeName = scopeElement.getAttributeValue(InspectionProfileImpl.NAME);
        if (scopeName != null) {
          final NamedScope namedScope = profileManager.getScopesManager().getScope(scopeName);
          if (namedScope != null) {
            final String errorLevel = scopeElement.getAttributeValue(InspectionProfileImpl.LEVEL_TAG);
            final String enabledInScope = scopeElement.getAttributeValue(InspectionProfileImpl.ENABLED_TAG);
            final InspectionProfileEntry InspectionProfileEntry =
              ((InspectionProfileImpl)profile).myRegistrar.createInspectionTool(myShortName, tool);
            InspectionProfileEntry.readSettings(scopeElement);
            addTool(namedScope, InspectionProfileEntry, enabledInScope != null && Boolean.parseBoolean(enabledInScope),
                    errorLevel != null ? HighlightDisplayLevel
                      .find(((SeverityProvider)profileManager).getOwnSeverityRegistrar().getSeverity(errorLevel)) : level);
          }
        }
      }
    }
  }

  public InspectionProfileEntry getTool() {
    if (myTools == null) return myDefaultState.getTool();
    return myTools.iterator().next().getTool();
  }

  @NotNull
  public List<ScopeToolState> getTools() {
    if (myTools == null) return Collections.singletonList(myDefaultState);
    List<ScopeToolState> result = new ArrayList<ScopeToolState>(myTools);
    result.add(myDefaultState);
    return result;
  }

  public ScopeToolState getDeafultState() {
    return myDefaultState;
  }

  public List<NamedScope> getScopes() {
    final List<NamedScope> result = new ArrayList<NamedScope>();
    if (myTools != null) {
      for (ScopeToolState state : myTools) {
        result.add(state.getScope());
      }
    }
    else {
      result.add(null);
    }
    return result;
  }

  public void removeScope(int scopeIdx) {
    if (myTools != null && scopeIdx >= 0 && myTools.size() > scopeIdx) {
      myTools.remove(scopeIdx);
      if (myTools.isEmpty()) {
        myTools = null;
      }
    }
  }

  public void setScope(int idx, NamedScope namedScope) {
    if (myTools != null && myTools.size() > idx && idx >= 0) {
      final ScopeToolState scopeToolState = myTools.get(idx);
      final InspectionProfileEntry tool = scopeToolState.getTool();
      myTools.remove(idx);
      myTools.add(idx, new ScopeToolState(namedScope, tool, scopeToolState.isEnabled(), scopeToolState.getLevel()));
    }
  }

  public void moveScope(int idx, int dir) {
    if (myTools != null && idx >= 0 && idx < myTools.size() && idx + dir >= 0 && idx + dir < myTools.size()) {
      final ScopeToolState state = myTools.get(idx);
      myTools.set(idx, myTools.get(idx + dir));
      myTools.set(idx + dir, state);
    }
  }

  public boolean isEnabled(NamedScope namedScope) {
    if (!myEnabled) return false;
    if (myDefaultState.isEnabled() && myTools != null) {
      for (ScopeToolState state : myTools) {
        if (Comparing.equal(namedScope, state.getScope())) return state.isEnabled();
      }
    }
    return myDefaultState.isEnabled();
  }

  public HighlightDisplayLevel getLevel(PsiElement element) {
    if (myTools == null || element == null) return myDefaultState.getLevel();
    final DependencyValidationManager manager = DependencyValidationManager.getInstance(element.getProject());
    for (ScopeToolState state : myTools) {
      final PackageSet set = state.getScope().getValue();
      if (set != null && set.contains(element.getContainingFile(), manager)) {
        return state.getLevel();
      }
    }
    return myDefaultState.getLevel();
  }



  public HighlightDisplayLevel getLevel() {
    return myDefaultState.getLevel();
  }

  public boolean isEnabled() {
    return myEnabled;
  }


  public boolean isEnabled(PsiElement element) {
    if (!myEnabled) return false;
    if (myTools == null || element == null) return myDefaultState.isEnabled();
    final DependencyValidationManager manager = DependencyValidationManager.getInstance(element.getProject());
    for (ScopeToolState state : myTools) {
      final PackageSet set = state.getScope().getValue();
      if (set != null && set.contains(element.getContainingFile(), manager)) {
        return state.isEnabled();
      }
    }
    return myDefaultState.isEnabled();
  }

  @Nullable
  public InspectionTool getEnabledTool(PsiElement element) {
    if (!myEnabled) return null;
    if (myTools == null || element == null) return myDefaultState.isEnabled() ? (InspectionTool)myDefaultState.getTool() : null;
    final DependencyValidationManager manager = DependencyValidationManager.getInstance(element.getProject());
    for (ScopeToolState state : myTools) {
      final PackageSet set = state.getScope().getValue();
      if (set != null && set.contains(element.getContainingFile(), manager)) {
        return state.isEnabled() ? (InspectionTool)state.getTool() : null;
      }
    }
    return myDefaultState.isEnabled() ? (InspectionTool)myDefaultState.getTool() : null;
  }

  public void enableTool() {
    myEnabled = true;
  }

  public void enableTool(NamedScope namedScope) {
    if (myTools != null) {
      for (ScopeToolState state : myTools) {
        if (Comparing.equal(state.getScope(), namedScope)) {
          state.setEnabled(true);
        }
      }
    }
  }

  public void enableTool(PsiElement element) {
    myEnabled = true;
    if (element == null) return;
    final DependencyValidationManager validationManager = DependencyValidationManager.getInstance(element.getProject());
    if (myTools != null) {
      for (ScopeToolState state : myTools) {
        final NamedScope scope = state.getScope();
        if (scope != null && scope.getValue().contains(element.getContainingFile(), validationManager)) {
          state.setEnabled(true);
          return;
        }
      }
    }
  }


  public void disableTool(NamedScope namedScope) {
    if (myTools != null) {
      for (ScopeToolState state : myTools) {
        if (Comparing.equal(state.getScope(), namedScope)) {
          state.setEnabled(false);
        }
      }
    }
  }

  public void disableTool() {
    myEnabled = false;
  }


  public void disableTool(PsiElement element) {
    if (element == null) return;
    final DependencyValidationManager validationManager = DependencyValidationManager.getInstance(element.getProject());
    if (myTools != null) {
      for (ScopeToolState state : myTools) {
        final NamedScope scope = state.getScope();
        if (scope != null && scope.getValue().contains(element.getContainingFile(), validationManager)) {
          state.setEnabled(false);
          return;
        }
      }
    } else {
      disableTool();
    }
  }

  public HighlightDisplayLevel getLevel(final NamedScope scope) {
    if (myTools != null){
      for (ScopeToolState state : myTools) {
        if (Comparing.equal(state.getScope(), scope)) {
          return state.getLevel();
        }
      }
    }
    return myDefaultState.getLevel();
  }

   public boolean equalTo(ToolsImpl tools) {
      if (myEnabled != tools.myEnabled) return false;
      if (getTools().size() != tools.getTools().size()) return false;
      for (int i = 0; i < getTools().size(); i++) {
        final ScopeToolState state = getTools().get(i);
        final ScopeToolState toolState = tools.getTools().get(i);
        if (!state.equalTo(toolState)){
          return false;
        }
      }
      return true;

  }



  public void setLevel(HighlightDisplayLevel level, int idx) {
    if (myTools != null && myTools.size() > idx && idx >= 0) {
      final ScopeToolState scopeToolState = myTools.get(idx);
      myTools.remove(idx);
      myTools.add(idx, new ScopeToolState(scopeToolState.getScope(), scopeToolState.getTool(), scopeToolState.isEnabled(), level));
    }
  }

  public void setTool(InspectionProfileEntry inspectionTool) {
    myDefaultState.setTool(inspectionTool);
  }

  public void setLevel(HighlightDisplayLevel level) {
    myDefaultState.setLevel(level);
  }

  @Nullable
  public List<ScopeToolState> getNonDefaultTools() {
    return myTools;
  }
}