/*
 * This file is part of LSPosed.
 *
 * LSPosed is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LSPosed is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LSPosed.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2020 EdXposed Contributors
 * Copyright (C) 2021 LSPosed Contributors
 */

package io.github.lsposed.lspd.nativebridge;

import java.lang.reflect.Member;
import java.lang.reflect.Method;

public class Yahfa {

    public static native boolean backupAndHookNative(Object target, Method hook, Method backup);

    // JNI.ToReflectedMethod() could return either Method or Constructor
    public static native Member findMethodNative(Class targetClass, String methodName, String methodSig);

    public static native void init(int sdkVersion);

    public static native void recordHooked(Member member);

    public static native boolean isHooked(Member member);

    public static native Class<?> buildHooker(ClassLoader appClassLoader, Class<?> returnType, Class<?>[] params);
}
