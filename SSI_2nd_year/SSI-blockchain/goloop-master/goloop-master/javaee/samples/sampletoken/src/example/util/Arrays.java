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

package example.util;

import score.ArrayDB;

import java.math.BigInteger;

public class Arrays {
    public static void remove(ArrayDB<BigInteger> array, BigInteger key) {
        BigInteger last = array.pop();
        if (!last.equals(key)) {
            int i = array.size();
            while (--i >= 0) {
                if (key.equals(array.get(i))) {
                    array.set(i, last);
                    break;
                }
            }
        }
    }
}
