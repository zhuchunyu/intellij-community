package de.plushnikov.intellij.plugin.intention;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import de.plushnikov.intellij.plugin.LombokBundle;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.thirdparty.LombokUtils;
import de.plushnikov.intellij.plugin.util.LombokProcessorUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * @author Lekanich
 */
public final class ReplaceWithLombokAnnotationAction extends AbstractLombokIntentionAction {

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiElement element) {
    if (super.getPresentation(context, element) == null) return null;
    if (!(element instanceof PsiIdentifier)) return null;

    PsiElement parent = PsiTreeUtil.getParentOfType(element, PsiField.class, PsiMethod.class);
    boolean available = false;
    if (parent instanceof PsiField) {
      available = Stream.of(findGetterMethodToReplace((PsiField)parent), findSetterMethodToReplace((PsiField)parent))
        .anyMatch(Optional::isPresent);
    }
    else if (parent instanceof PsiMethod) {
      available = Stream.of(findAnchorFieldForGetter((PsiMethod)parent), findAnchorFieldForSetter((PsiMethod)parent))
        .anyMatch(Optional::isPresent);
    }
    if (!available) return null;
    return Presentation.of(LombokBundle.message("intention.name.replace.with.lombok"));
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    PsiElement parent = PsiTreeUtil.getParentOfType(element, PsiVariable.class, PsiClass.class, PsiMethod.class);

    if (parent instanceof PsiField) {
      handleField((PsiField)parent);
    }
    else if (parent instanceof PsiMethod) {
      handleMethod((PsiMethod)parent);
    }
  }

  private static void handleMethod(@NotNull PsiMethod psiMethod) {
    findAnchorFieldForGetter(psiMethod)
      .map(PsiField::getModifierList)
      .ifPresent(modifierList -> replaceWithAnnotation(modifierList, psiMethod, LombokClassNames.GETTER));

    findAnchorFieldForSetter(psiMethod)
      .map(PsiField::getModifierList)
      .ifPresent(modifierList -> replaceWithAnnotation(modifierList, psiMethod, LombokClassNames.SETTER));
  }

  private static Optional<PsiMethod> findGetterMethodToReplace(@NotNull PsiField psiField) {
    final PsiMethod getterForField = PropertyUtilBase.findGetterForField(psiField);
    if (null != getterForField && !(getterForField instanceof LombokLightMethodBuilder)) {
      if (findAnchorFieldForGetter(getterForField).filter(psiField::equals).isPresent()) {
        return Optional.of(getterForField);
      }
    }
    return Optional.empty();
  }

  private static Optional<PsiMethod> findSetterMethodToReplace(@NotNull PsiField psiField) {
    final PsiMethod setterForField = PropertyUtilBase.findSetterForField(psiField);
    if (null != setterForField && !(setterForField instanceof LombokLightMethodBuilder)) {
      if (findAnchorFieldForSetter(setterForField).filter(psiField::equals).isPresent()) {
        return Optional.of(setterForField);
      }
    }
    return Optional.empty();
  }

  private static void handleField(@NotNull PsiField psiField) {
    PsiModifierList psiFieldModifierList = psiField.getModifierList();
    if (null == psiFieldModifierList) {
      return;
    }

    // replace getter if it matches the requirements
    findGetterMethodToReplace(psiField).ifPresent(
      psiMethod -> replaceWithAnnotation(psiFieldModifierList, psiMethod, LombokClassNames.GETTER)
    );

    // replace setter if it matches the requirements
    findSetterMethodToReplace(psiField).ifPresent(
      psiMethod -> replaceWithAnnotation(psiFieldModifierList, psiMethod, LombokClassNames.SETTER)
    );
  }

  private static Optional<PsiField> findAnchorFieldForSetter(@NotNull PsiMethod method) {
    // it seems wrong to replace abstract possible getters
    // abstract methods maybe the part of interface so let them live
    if (!PropertyUtilBase.isSimplePropertySetter(method) || method.hasModifierProperty(PsiModifier.ABSTRACT)) {
      return Optional.empty();
    }

    // check the parameter list
    // it should have 1 parameter with the same type
    if (Optional.of(method.getParameterList())
      .filter(paramList -> paramList.getParametersCount() == 1)
      .map(paramList -> paramList.getParameter(0))
      .map(PsiParameter::getType)
      .filter(expectedType -> Optional.ofNullable(method.getContainingClass())
        .map(PsiClassUtil::collectClassFieldsIntern)
        .orElse(Collections.emptyList())
        .stream()
        .filter(field -> method.getName().equals(LombokUtils.getSetterName(field)))
        .noneMatch(field -> expectedType.equals(field.getType()))
      ).isPresent()) {
      return Optional.empty();
    }

    PsiCodeBlock body = method.getBody();
    if (body == null) {
      return Optional.ofNullable(method.getContainingClass())
        .map(PsiClassUtil::collectClassFieldsIntern)
        .orElse(Collections.emptyList())
        .stream()
        .filter(field -> method.getName().equals(LombokUtils.getSetterName(field)))
        .findAny();
    }
    else if (body.getStatementCount() == 1) {
      // validate that the method body doesn't contain anything additional
      // and also contain proper assign statement
      Optional<PsiAssignmentExpression> assignmentExpression = Optional.of(body.getStatements()[0])
        .filter(PsiExpressionStatement.class::isInstance)
        .map(PsiExpressionStatement.class::cast)
        .map(PsiExpressionStatement::getExpression)
        .filter(PsiAssignmentExpression.class::isInstance)
        .map(PsiAssignmentExpression.class::cast);

      if (assignmentExpression.map(PsiAssignmentExpression::getRExpression)
        .filter(PsiReferenceExpression.class::isInstance).map(PsiReferenceExpression.class::cast)
        .map(PsiReferenceExpression::resolve).filter(PsiParameter.class::isInstance).isPresent()) {

        return assignmentExpression.map(PsiAssignmentExpression::getLExpression)
          .filter(PsiReferenceExpression.class::isInstance).map(PsiReferenceExpression.class::cast)
          .map(PsiReferenceExpression::resolve).filter(PsiField.class::isInstance)
          .map(PsiField.class::cast);
      }
    }
    return Optional.empty();
  }

  private static Optional<PsiField> findAnchorFieldForGetter(@NotNull PsiMethod method) {
    // it seems wrong to replace abstract possible getters
    // abstract methods maybe the part of interface so let them live
    if (!PropertyUtilBase.isSimplePropertyGetter(method) || method.hasModifierProperty(PsiModifier.ABSTRACT)) {
      return Optional.empty();
    }

    PsiCodeBlock body = method.getBody();
    if (body == null) {
      return Optional.ofNullable(method.getContainingClass())
        .map(PsiClassUtil::collectClassFieldsIntern)
        .orElse(Collections.emptyList())
        .stream()
        .filter(field -> method.getName().equals(LombokUtils.getGetterName(field)))
        .findAny();
    }
    else if (body.getStatementCount() == 1) {
      return Optional.of(body.getStatements()[0])
        .filter(PsiReturnStatement.class::isInstance)
        .map(PsiReturnStatement.class::cast)
        .map(PsiReturnStatement::getReturnValue)
        .map(PsiUtil::deparenthesizeExpression)
        .filter(PsiReferenceExpression.class::isInstance)
        .map(PsiReferenceExpression.class::cast)
        .map(PsiReferenceExpression::resolve)
        .filter(PsiField.class::isInstance)
        .map(PsiField.class::cast);
    }
    return Optional.empty();
  }

  private static void replaceWithAnnotation(@NotNull PsiModifierList modifierList,
                                            @NotNull PsiMethod method,
                                            @NotNull String annotationName) {
    final Optional<String> accessLevelFQN = LombokProcessorUtil.convertModifierToLombokAccessLevel(method);

    method.delete();

    PsiAnnotation addedAnnotation = modifierList.addAnnotation(annotationName);
    if (accessLevelFQN.isPresent()) {
      PsiExpression accessLevelExpression = PsiElementFactory.getInstance(modifierList.getProject())
        .createExpressionFromText(accessLevelFQN.get(), null);
      addedAnnotation.setDeclaredAttributeValue(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME, accessLevelExpression);
    }
  }

  @Override
  public @NotNull String getFamilyName() {
    return LombokBundle.message("replace.with.annotations.lombok");
  }
}
