package com.mirada.os;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Another process on a config folder, for {@link InstanceLockTest}: claims the folder named by its
 * argument, prints "primary" or "secondary", answers each "probe" line with whether it sees others,
 * and lets the claim go when its input closes.
 */
public final class InstanceLockProbe {

    private InstanceLockProbe() {}

    public static void main(String[] args) throws Exception {
        try (InstanceLock lock = InstanceLock.claim(Path.of(args[0]))) {
            System.out.println(lock.primary() ? "primary" : "secondary");
            System.out.flush();
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            for (String line = in.readLine(); line != null; line = in.readLine()) {
                System.out.println("others=" + lock.othersPresent());
                System.out.flush();
            }
        }
    }
}
