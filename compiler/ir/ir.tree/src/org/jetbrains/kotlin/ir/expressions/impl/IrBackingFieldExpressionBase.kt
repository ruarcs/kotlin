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

import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.ir.*
import org.jetbrains.kotlin.ir.expressions.IrBackingFieldExpression
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrOperator
import org.jetbrains.kotlin.types.KotlinType

abstract class IrBackingFieldExpressionBase(
        startOffset: Int,
        endOffset: Int,
        descriptor: PropertyDescriptor,
        type: KotlinType,
        override val operator: IrOperator? = null,
        override val superQualifier: ClassDescriptor? = null
) : IrDeclarationReferenceBase<PropertyDescriptor>(startOffset, endOffset, type, descriptor), IrBackingFieldExpression {
    override final var receiver: IrExpression? = null
        set(value) {
            value?.assertDetached()
            field?.detach()
            field = value
            value?.setTreeLocation(this, BACKING_FIELD_RECEIVER_SLOT)
        }

    override fun getChild(slot: Int): IrElement? =
            when (slot) {
                BACKING_FIELD_RECEIVER_SLOT -> receiver
                else -> null
            }

    override fun replaceChild(slot: Int, newChild: IrElement) {
        when (slot) {
            BACKING_FIELD_RECEIVER_SLOT -> receiver = newChild.assertCast()
            else -> throwNoSuchSlot(slot)
        }
    }
}