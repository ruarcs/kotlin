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

package org.jetbrains.kotlin.ir.expressions.impl

import org.jetbrains.kotlin.ir.*
import org.jetbrains.kotlin.ir.expressions.IrErrorCallExpression
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.visitors.IrElementVisitor
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.utils.SmartList

class IrErrorCallExpressionImpl(
        startOffset: Int,
        endOffset: Int,
        type: KotlinType,
        override val description: String
) : IrExpressionBase(startOffset, endOffset, type), IrErrorCallExpression {
    override var explicitReceiver: IrExpression? = null
        set(value) {
            field?.detach()
            field = value
            value?.setTreeLocation(this, DISPATCH_RECEIVER_SLOT)
        }

    override val arguments: MutableList<IrExpression> = SmartList()

    fun addArgument(argument: IrExpression) {
        argument.setTreeLocation(this, arguments.size)
        arguments.add(argument)
    }

    override fun <R, D> accept(visitor: IrElementVisitor<R, D>, data: D): R {
        return visitor.visitErrorCallExpression(this, data)
    }

    override fun <D> acceptChildren(visitor: IrElementVisitor<Unit, D>, data: D) {
        explicitReceiver?.accept(visitor, data)
        arguments.forEach { it.accept(visitor, data) }
    }

    override fun getChild(slot: Int): IrElement? =
            arguments.getOrNull(slot)

    override fun replaceChild(slot: Int, newChild: IrElement) {
        if (slot < 0 || slot >= arguments.size) throwNoSuchSlot(slot)

        arguments[slot].detach()
        arguments[slot] = newChild.assertCast()
        newChild.setTreeLocation(this, slot)
    }
}