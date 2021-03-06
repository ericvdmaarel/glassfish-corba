/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 1994-2018 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://oss.oracle.com/licenses/CDDL+GPL-1.1
 * or LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package org.glassfish.rmic.tools.tree;

import org.glassfish.rmic.tools.java.*;
import org.glassfish.rmic.tools.asm.Assembler;

/**
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 */
public
class AssignAddExpression extends AssignOpExpression {
    /**
     * Constructor
     */
    public AssignAddExpression(long where, Expression left, Expression right) {
        super(ASGADD, where, left, right);
    }


    /**
     * The cost of inlining this statement
     */
    public int costInline(int thresh, Environment env, Context ctx) {
        return type.isType(TC_CLASS) ? 25 : super.costInline(thresh, env, ctx);
    }

    /**
     * Code
     */
    void code(Environment env, Context ctx, Assembler asm, boolean valNeeded) {
        if (itype.isType(TC_CLASS)) {
            // Create code for     String += <value>
            try {
                // Create new string buffer.
                Type argTypes[] = {Type.tString};
                ClassDeclaration c =
                    env.getClassDeclaration(idJavaLangStringBuffer);

                if (updater == null) {

                    // No access method is needed.

                    asm.add(where, opc_new, c);
                    asm.add(where, opc_dup);
                    // stack: ...<buffer><buffer>
                    int depth = left.codeLValue(env, ctx, asm);
                    codeDup(env, ctx, asm, depth, 2); // copy past 2 string buffers
                    // stack: ...[<getter args>]<buffer><buffer>[<getter args>]
                    // where <buffer> isn't yet initialized, and the <getter args>
                    // has length depth and is whatever is needed to get/set the
                    // value
                    left.codeLoad(env, ctx, asm);
                    left.ensureString(env, ctx, asm);  // Why is this needed?
                    // stack: ...[<getter args>]<buffer><buffer><string>
                    // call .<init>(String)
                    ClassDefinition sourceClass = ctx.field.getClassDefinition();
                    MemberDefinition f = c.getClassDefinition(env)
                        .matchMethod(env, sourceClass,
                                     idInit, argTypes);
                    asm.add(where, opc_invokespecial, f);
                    // stack: ...[<getter args>]<initialized buffer>
                    // .append(value).toString()
                    right.codeAppend(env, ctx, asm, c, false);
                    f = c.getClassDefinition(env)
                        .matchMethod(env, sourceClass, idToString);
                    asm.add(where, opc_invokevirtual, f);
                    // stack: ...[<getter args>]<string>
                    // dup the string past the <getter args>, if necessary.
                    if (valNeeded) {
                        codeDup(env, ctx, asm, Type.tString.stackSize(), depth);
                        // stack: ...<string>[<getter args>]<string>
                    }
                    // store
                    left.codeStore(env, ctx, asm);

                } else {

                    // Access method is required.
                    // (Handling this case fixes 4102566.)

                    updater.startUpdate(env, ctx, asm, false);
                    // stack: ...[<getter args>]<string>
                    left.ensureString(env, ctx, asm);  // Why is this needed?
                    asm.add(where, opc_new, c);
                    // stack: ...[<getter args>]<string><buffer>
                    asm.add(where, opc_dup_x1);
                    // stack: ...[<getter args>]<buffer><string><buffer>
                    asm.add(where, opc_swap);
                    // stack: ...[<getter args>]<buffer><buffer><string>
                    // call .<init>(String)
                    ClassDefinition sourceClass = ctx.field.getClassDefinition();
                    MemberDefinition f = c.getClassDefinition(env)
                        .matchMethod(env, sourceClass,
                                     idInit, argTypes);
                    asm.add(where, opc_invokespecial, f);
                    // stack: ...[<getter args>]<initialized buffer>
                    // .append(value).toString()
                    right.codeAppend(env, ctx, asm, c, false);
                    f = c.getClassDefinition(env)
                        .matchMethod(env, sourceClass, idToString);
                    asm.add(where, opc_invokevirtual, f);
                    // stack: .. [<getter args>]<string>
                    updater.finishUpdate(env, ctx, asm, valNeeded);

                }

            } catch (ClassNotFound e) {
                throw new CompilerError(e);
            } catch (AmbiguousMember e) {
                throw new CompilerError(e);
            }
        } else {
            super.code(env, ctx, asm, valNeeded);
        }
    }

    /**
     * Code
     */
    void codeOperation(Environment env, Context ctx, Assembler asm) {
        asm.add(where, opc_iadd + itype.getTypeCodeOffset());
    }
}
