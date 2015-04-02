package org.jetbrains.plugins.groovy.lang.flow.instruction;

import com.intellij.codeInspection.dataFlow.*;
import com.intellij.codeInspection.dataFlow.instructions.Instruction;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PropertyUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil;

import java.util.Map;

@SuppressWarnings({"MethodMayBeStatic", "UnnecessaryLocalVariable", "StatementWithEmptyBody"})
public class GrMethodCallInstruction<V extends GrInstructionVisitor<V>> extends Instruction<V> {

  private final @NotNull GrExpression myCall;

  private final @NotNull GrNamedArgument[] myNamedArguments;
  private final @NotNull GrExpression[] myExpressionArguments;
  private final @NotNull GrClosableBlock[] myClosureArguments;

  private final @Nullable PsiType myReturnType;
  private final @Nullable PsiMethod myTargetMethod;

  private final boolean mySafeCall;
  private final boolean myShouldFlushFields;

  private final @Nullable Map<GrExpression, Pair<PsiParameter, PsiType>> argumentsToParameters;
  private final @Nullable DfaValue myPrecalculatedReturnValue;


  public GrMethodCallInstruction(@NotNull GrReferenceExpression propertyAccess, @NotNull PsiMethod property) {
    myCall = propertyAccess;
    myNamedArguments = GrNamedArgument.EMPTY_ARRAY;
    myExpressionArguments = GrExpression.EMPTY_ARRAY;
    myClosureArguments = GrClosableBlock.EMPTY_ARRAY;

    myReturnType = property.getReturnType();
    myTargetMethod = property;

    mySafeCall = isSafeAccess(propertyAccess);
    myShouldFlushFields = false;
    argumentsToParameters = null;
    myPrecalculatedReturnValue = null;
  }

  public GrMethodCallInstruction(@NotNull GrCallExpression call, @Nullable DfaValue precalculatedReturnValue) {
    final GroovyResolveResult result = call.advancedResolve();

    myCall = call;
    myNamedArguments = call.getNamedArguments();
    myExpressionArguments = call.getExpressionArguments();
    myClosureArguments = call.getClosureArguments();

    myReturnType = myCall.getType();
    myTargetMethod = (PsiMethod)result.getElement();

    mySafeCall = call instanceof GrMethodCall && isSafeAccess(((GrMethodCall)call).getInvokedExpression());
    myShouldFlushFields = !(call instanceof GrNewExpression && myReturnType != null && myReturnType.getArrayDimensions() > 0)
                          && !isPureCall(myTargetMethod);
    argumentsToParameters = GrClosureSignatureUtil.mapArgumentsToParameters(
      result, call, false, false, call.getNamedArguments(), myExpressionArguments, myClosureArguments
    );
    myPrecalculatedReturnValue = precalculatedReturnValue;
  }

  public Nullness getParameterNullability(GrExpression e) {
    Pair<PsiParameter, PsiType> p = argumentsToParameters == null ? null : argumentsToParameters.get(e);
    PsiParameter parameter = p == null ? null : p.first;
    PsiType type = p == null ? null : p.second;
    if (parameter == null || type == null) return Nullness.UNKNOWN;
    return DfaPsiUtil.getElementNullability(type, parameter);
  }

  @NotNull
  public GrExpression getCall() {
    return myCall;
  }

  @NotNull
  public GrNamedArgument[] getNamedArguments() {
    return myNamedArguments;
  }

  @NotNull
  public GrExpression[] getExpressionArguments() {
    return myExpressionArguments;
  }

  @NotNull
  public GrClosableBlock[] getClosureArguments() {
    return myClosureArguments;
  }

  @Nullable
  public PsiType getReturnType() {
    return myReturnType;
  }

  @Nullable
  public PsiMethod getTargetMethod() {
    return myTargetMethod;
  }

  public boolean isSafeCall() {
    return mySafeCall;
  }

  public boolean shouldFlushFields() {
    return myShouldFlushFields;
  }

  @Nullable
  public DfaValue getPrecalculatedReturnValue() {
    return myPrecalculatedReturnValue;
  }

  @Override
  public DfaInstructionState<V>[] accept(@NotNull DfaMemoryState stateBefore, @NotNull V visitor) {
    return visitor.visitMethodCallGroovy(this, stateBefore);
  }

  public String toString() {
    return "CALL METHOD " + myCall.getText();
  }

  private static boolean isSafeAccess(GrExpression referenceExpression) {
    return referenceExpression instanceof GrReferenceExpression &&
           ((GrReferenceExpression)referenceExpression).getDotTokenType() == GroovyTokenTypes.mOPTIONAL_DOT;
  }

  private static boolean isPureCall(PsiMethod myTargetMethod) {
    if (myTargetMethod == null) return false;
    return ControlFlowAnalyzer.isPure(myTargetMethod) || PropertyUtil.isSimplePropertyGetter(myTargetMethod);
  }
}
