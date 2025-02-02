/*
 * Copyright 2020 ICON Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package foundation.icon.ee.score;

import foundation.icon.ee.struct.Member;
import foundation.icon.ee.struct.MemberDecl;
import foundation.icon.ee.struct.MethodCollector;
import foundation.icon.ee.struct.StructDB;
import foundation.icon.ee.types.Method;
import foundation.icon.ee.util.ASM;
import foundation.icon.ee.util.MethodUnpacker;
import org.aion.avm.core.dappreading.LoadedJar;
import org.aion.avm.utilities.JarBuilder;
import org.aion.avm.utilities.Utilities;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Validator {
    private static ValidationException fail(String fmt, Object... args) throws
            ValidationException {
        throw new ValidationException(String.format(fmt, args));
    }

    /**
     * Returns false if code is invalid.
     * @return false if code is invalid
     * @throws IOException for other critical error
     * @throws ValidationException for validation error
     */
    public static Method[] validate(byte[] codeBytes) throws
            IOException, ValidationException {
        var apisBytes = JarBuilder.getAPIsBytesFromJAR(codeBytes);
        if (apisBytes == null) {
            throw fail("Cannot get APIS");
        }
        var jar = LoadedJar.fromBytes(codeBytes);
        var classMap = jar.classBytesByQualifiedNames;
        var structDB = new StructDB(classMap);
        Method[] eeMethods;
        try {
            eeMethods = MethodUnpacker.readFrom(apisBytes);
        } catch (IOException e) {
            throw fail ("bad APIS format");
        }
        String cur = jar.mainClassName;
        Map<Member, MemberDecl> mmap = new HashMap<>();
        while (cur != null) {
            var classBytes = classMap.get(cur);
            if (classBytes==null) {
                break;
            }
            var cv = ASM.accept(classBytes, new MethodCollector());
            for (var m : cv.getMethodDecls()) {
                mmap.putIfAbsent(m.getMember(), m);
            }
            cur = Utilities.internalNameToFullyQualifiedName(cv.getSuperName());
        }
        Set<String> eeMethodNames = new HashSet<>();
        for (var eem : eeMethods) {
            if (!eeMethodNames.add(eem.getName())) {
                throw fail("Duplicated external/event method %s",
                        eem.getDebugName());
            }
            if (eem.getType()==Method.MethodType.EVENT) {
                continue;
            }
            var m = mmap.get(new Member(eem.getName(), eem.getDescriptor()));
            if (m == null) {
                throw fail("No such method %s", eem.getDebugName());
            }
            if ((m.getAccess()& Modifier.PUBLIC)==0
                    || (m.getAccess()&Modifier.STATIC)!=0) {
                throw fail("Non public or static method %s", eem.getDebugName());
            }
            var mt = Type.getType(m.getDescriptor());
            var rt = mt.getReturnType();
            if (!structDB.isValidReturnType(rt)) {
                throw fail("Invalid return type for method %s",
                        eem.getDebugName());
            }
            if (eem.getOutput() != structDB.getEEPTypeFromReturnType(rt)) {
                throw fail("Incompatible return type for method %s",
                        eem.getDebugName());
            }

            var eeParams = eem.getInputs();
            var pts = mt.getArgumentTypes();
            if (eeParams.length != pts.length) {
                throw fail("Bad param length in APIS for method %s",
                        eem.getDebugName());
            }
            for (int i=0; i<eeParams.length; ++i) {
                var eep = eeParams[i];
                var pt = pts[i];
                var ptd = structDB.getDetailFromParameterType(pt);
                if (!eep.getTypeDetail().equals(ptd)) {
                    throw fail("Incompatible param %s for method %s",
                            eep.getName(), eem.getDebugName());
                }
                if (!structDB.isValidParamType(pt)) {
                    throw fail("Invalid param %s for method %s",
                            eep.getName(), eem.getDebugName());
                }
            }
        }
        return eeMethods;
    }
}
