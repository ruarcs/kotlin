/*
 * Copyright 2010-2016 JetBrains s.r.o.
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

package org.jetbrains.kotlin.idea.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.inspections.IntentionBasedInspection
import org.jetbrains.kotlin.lexer.KtToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getStartOffsetIn
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameUnsafe

class ReplaceSingleLineLetInspection : IntentionBasedInspection<KtCallExpression>(ReplaceSingleLineLetIntention::class) {
    override fun inspectionRange(element: KtCallExpression) = element.calleeExpression?.let {
        val start = it.getStartOffsetIn(element)
        TextRange(start, start + it.endOffset - it.startOffset)
    }
}

class ReplaceSingleLineLetIntention : SelfTargetingOffsetIndependentIntention<KtCallExpression>(
        KtCallExpression::class.java,
        "Replace single line '.let' call"
) {
    override fun applyTo(element: KtCallExpression, editor: Editor?) {
        val lambdaExpression = element.lambdaArguments.firstOrNull()?.getLambdaExpression() ?: return
        val bodyExpression = lambdaExpression.bodyExpression?.children?.singleOrNull() ?: return
        val dotQualifiedExpression = bodyExpression as? KtDotQualifiedExpression ?: return
        val parent = element.parent
        val factory = KtPsiFactory(element.project)
        when (parent) {
            is KtQualifiedExpression -> {
                val sign = parent.operationSign
                val receiver = parent.receiverExpression
                parent.replace(dotQualifiedExpression.replaceFirstReceiver(factory, receiver, sign))
            }
            else -> {
                element.replace(dotQualifiedExpression.replaceFirstReceiver(factory, null, KtTokens.DOT))
            }
        }
    }

    private fun KtDotQualifiedExpression.replaceFirstReceiver(
            factory: KtPsiFactory,
            newReceiver: KtExpression?,
            sign: KtToken
    ): KtExpression {
        val receiver = receiverExpression
        return when (receiver) {
            is KtDotQualifiedExpression -> {
                selectorExpression?.let {
                    factory.createExpressionByPattern("$0.$1", receiver.replaceFirstReceiver(factory, newReceiver, sign), it)
                } ?: receiver.replaceFirstReceiver(factory, newReceiver, sign)
            }
            else -> {
                if (newReceiver == null) {
                    selectorExpression ?: this
                }
                else {
                    val op = if (sign == KtTokens.SAFE_ACCESS) "?." else "."
                    selectorExpression?.let { factory.createExpressionByPattern("$0$op$1", newReceiver, it) }
                    ?: newReceiver
                }
            }
        }
    }

    override fun isApplicableTo(element: KtCallExpression): Boolean {
        if (!isLetMethod(element)) return false
        val lambdaExpression = element.lambdaArguments.firstOrNull()?.getLambdaExpression() ?: return false
        val bodyExpression = lambdaExpression.bodyExpression?.children?.singleOrNull() ?: return false
        val dotQualifiedExpression = bodyExpression as? KtDotQualifiedExpression ?: return false
        val parameterName = lambdaExpression.getParameterName() ?: return false
        val receiverExpression = dotQualifiedExpression.getLeftMostReceiverExpression()
        if (receiverExpression.text != parameterName) return false
        return !dotQualifiedExpression.receiverUsedAsArgument(parameterName)
    }

    private fun isLetMethod(element: KtCallExpression): Boolean {
        if (element.calleeExpression?.text != "let") return false
        return isMethodCall(element, "kotlin.let")
    }

    private fun isMethodCall(expression: KtExpression, fqMethodName: String): Boolean {
        val resolvedCall = expression.getResolvedCall(expression.analyze()) ?: return false
        return resolvedCall.resultingDescriptor.fqNameUnsafe.asString() == fqMethodName
    }

    private fun KtLambdaExpression.getParameterName(): String? {
        val parameters = valueParameters
        if (parameters.size > 1) return null
        return if (parameters.size == 1) parameters[0].text else "it"
    }

    private fun KtDotQualifiedExpression.getLeftMostReceiverExpression(): KtExpression =
            (receiverExpression as? KtDotQualifiedExpression)?.getLeftMostReceiverExpression() ?: receiverExpression

    private fun KtDotQualifiedExpression.receiverUsedAsArgument(receiverName: String): Boolean {
        if ((selectorExpression as? KtCallExpression)?.valueArguments?.firstOrNull { it.text == receiverName } != null) return true
        return (receiverExpression as? KtDotQualifiedExpression)?.receiverUsedAsArgument(receiverName) ?: false
    }
}