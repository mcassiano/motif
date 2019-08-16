/*
 * Copyright (c) 2018-2019 Uber Technologies, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package motif;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * This API in in beta.
 *
 * https://github.com/uber/motif/issues/108
 *
 * @param <S> Scope class
 * @param <D> Dependencies interface
 */
@ScopeFactoryMarker
public class ScopeFactory<S, D> {

    public S create(D dependencies) {
        Class<?> factoryHelperClass = getFactoryHelperClass();
        Method createMethod = factoryHelperClass.getDeclaredMethods()[0];
        try {
            return (S) createMethod.invoke(null, dependencies);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    private Class<?> getFactoryHelperClass() {
        String helperClassName = getClass().getName() + "Helper";
        try {
            return Class.forName(helperClassName, false, getClass().getClassLoader());
        } catch (ClassNotFoundException e) {
            if (getClass().isAnonymousClass()) {
                throw new RuntimeException("Anonymous ScopeFactory classes are not supported.");
            } else {
                throw new RuntimeException("Could not find generated helper class " + helperClassName + ". Ensure " +
                        "that the Motif annotation processor is enabled.");
            }
        }
    }
}
