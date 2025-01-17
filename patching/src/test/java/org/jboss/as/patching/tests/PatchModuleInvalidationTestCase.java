/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.patching.tests;


import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import org.jboss.as.patching.HashUtils;
import org.jboss.as.patching.IoUtils;
import org.jboss.as.patching.installation.PatchableTarget;
import org.jboss.as.patching.runner.TestUtils;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.jboss.as.patching.runner.PatchUtils.BACKUP_EXT;
import static org.jboss.as.patching.runner.PatchUtils.JAR_EXT;
import static org.jboss.as.patching.runner.TestUtils.createModule0;
import static org.jboss.as.patching.runner.TestUtils.randomString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;

/**
 * @author Emanuel Muckenhuber
 */
public class PatchModuleInvalidationTestCase extends AbstractPatchingTest {

    private static final Boolean INVALIDATION_ENABLED = Boolean.getBoolean("org.wildfly.patching.jar.invalidation");
    private static final String MODULE_NAME = "org.jboss.test.module";
    private static final String RESOURCE = "resource0.jar";

    private static final TestUtils.ContentTask CONTENT_TASK = new TestUtils.ContentTask() {
        @Override
        public String[] writeContent(File target) throws IOException {
            writeJar(new File(target, RESOURCE));
            return new String[] { RESOURCE };
        }
    };

    @BeforeClass
    public static void checkValidationEnabled() {
        Assume.assumeTrue(INVALIDATION_ENABLED);
    }

    @Test
    public void test() throws Exception {

        final PatchingTestBuilder test = createDefaultBuilder();

        final File root = test.getRoot();
        final File installation = new File(root, JBOSS_INSTALLATION);
        final File moduleRoot = new File(installation, "modules/system/layers/base".replace('/', File.separatorChar));
        final File module0 = createModule0(moduleRoot, MODULE_NAME, CONTENT_TASK);
        final File resource = new File(module0, "main/resource0.jar".replace('/', File.separatorChar));
        final File resourceBackup = new File(module0, "main/resource0.jar.patched".replace('/', File.separatorChar));
        assertLoadable(resource);

        final byte[] existingHash = HashUtils.hashFile(module0);
        final byte[] resultingHash = Arrays.copyOf(existingHash, existingHash.length);

        final PatchingTestStepBuilder oop1 = test.createStepBuilder();
        oop1.setPatchId("oop1")
                .oneOffPatchIdentity(PRODUCT_VERSION)
                .oneOffPatchElement("base-oop1", "base", false)
                .updateModule(MODULE_NAME, existingHash, resultingHash, CONTENT_TASK)
                ;

        apply(oop1);
        assertThat(resourceBackup.exists(), is(true));
        assertThat(resource.exists(), is(false));
        assertNotLoadable(resourceBackup);

        // Module in patch oop1
        final File resource1 = getModuleResource("base-oop1", MODULE_NAME);
        assertLoadable(resource1);
        final File resource1Backup = getBackupFile(resource1);
        assertThat(resource1Backup.exists(), is(false));
        assertThat(resource1.exists(), is(true));

        final PatchingTestStepBuilder oop2 = test.createStepBuilder();
        oop2.setPatchId("oop2")
                .oneOffPatchIdentity(PRODUCT_VERSION)
                .oneOffPatchElement("base-oop2", "base", false)
                .updateModule(MODULE_NAME, resultingHash, null, CONTENT_TASK)
                ;

        apply(oop2);

        // Module in patch oop2
        final File resource2 = getModuleResource("base-oop2", MODULE_NAME);
        final File resource2Backup = getBackupFile(resource2);
        assertThat(resource2Backup.exists(), is(false));
        assertThat(resource2.exists(), is(true));

        assertNotLoadable(resourceBackup);
        assertThat(resource1Backup.exists(), is(true));
        assertThat(resource1.exists(), is(false));
        assertNotLoadable(resource1Backup);
        assertLoadable(resource2);

        final PatchingTestStepBuilder cp1 = test.createStepBuilder();
        cp1.setPatchId("cp1")
                .upgradeIdentity(PRODUCT_VERSION, PRODUCT_VERSION)
                .upgradeElement("base-cp1", "base", false)
                .updateModule(MODULE_NAME, existingHash, resultingHash, CONTENT_TASK)
                ;

        apply(cp1);

        // Module in patch cp1
        final File resource3 = getModuleResource("base-cp1", MODULE_NAME);
        final File resource3Backup = getBackupFile(resource3);
        assertThat(resource3Backup.exists(), is(false));
        assertThat(resource3.exists(), is(true));

        assertNotLoadable(resourceBackup);
        assertNotLoadable(resource1Backup);
        assertThat(resource2Backup.exists(), is(true));
        assertThat(resource2.exists(), is(false));
        assertNotLoadable(resource2Backup);
        assertLoadable(resource3);

        final PatchingTestStepBuilder cp2 = test.createStepBuilder();
        cp2.setPatchId("cp2")
                .upgradeIdentity(PRODUCT_VERSION, PRODUCT_VERSION)
                .upgradeElement("base-cp2", "base", false)
                .updateModule(MODULE_NAME, resultingHash, null, CONTENT_TASK)
                ;

        apply(cp2);

        // Module in patch cp2
        final File resource4 = getModuleResource("base-cp2", MODULE_NAME);

        assertNotLoadable(resourceBackup);
        assertNotLoadable(resource1Backup);
        assertNotLoadable(resource2Backup);
        assertThat(resource3Backup.exists(), is(true));
        assertThat(resource3.exists(), is(false));
        assertNotLoadable(resource3Backup);
        assertLoadable(resource4);

        rollback(cp2);

        assertNotLoadable(resourceBackup);
        assertNotLoadable(resource1Backup);
        assertNotLoadable(resource2Backup);
        assertThat(resource3Backup.exists(), is(false));
        assertThat(resource3.exists(), is(true));
        assertLoadable(resource3);

        rollback(cp1);

        assertNotLoadable(resourceBackup);
        assertNotLoadable(resource1Backup);
        assertThat(resource2Backup.exists(), is(false));
        assertThat(resource2.exists(), is(true));
        assertLoadable(resource2);

        rollback(oop2);
        assertNotLoadable(resourceBackup);
        assertThat(resource1Backup.exists(), is(false));
        assertThat(resource1.exists(), is(true));
        assertLoadable(resource1);

        rollback(oop1);
        assertThat(resourceBackup.exists(), is(false));
        assertThat(resource.exists(), is(true));
        assertLoadable(resource);
    }

    File getModuleResource(final String patchID, final String moduleName) throws IOException {
        return getModuleResource("base", patchID, moduleName);
    }

    File getModuleResource(final String layer, final String patchID, final String moduleName) throws IOException {
        final PatchableTarget.TargetInfo info = getLayer(layer).loadTargetInfo();
        final File root = info.getDirectoryStructure().getModulePatchDirectory(patchID);
        final File moduleRoot = TestUtils.getModuleRoot(root, moduleName);
        return new File(moduleRoot, RESOURCE);
    }

    static File writeJar(final File target) {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class)
                .addClass(TestClass.class)
                .add(new StringAsset(randomString()), "testResource")
                .addManifest();
        archive.as(ZipExporter.class).exportTo(target);
        return target;
    }

    static void assertLoadable(final File jar) throws Exception {
        final URL[] urls = new URL[] { jar.toURI().toURL() };
        final URLClassLoader cl = new URLClassLoader(urls);
        try {
            Assert.assertNotNull(cl.getResource("testResource"));
            final Class<?> clazz = cl.loadClass("org.jboss.as.patching.tests.TestClass");
            final Constructor<?> constructor = clazz.getConstructor(String.class);
            final Object instance = constructor.newInstance("test");
            Assert.assertNotNull(instance);
        } finally {
            IoUtils.safeClose(cl);
        }
    }

    static void assertNotLoadable(final File jar) throws Exception {
        final URL[] urls = new URL[] { jar.toURI().toURL() };
        final URLClassLoader cl = new URLClassLoader(urls, null);
        try {
            Assert.assertNull(cl.getResource("testResource"));
            try {
                cl.loadClass("org.jboss.as.patching.tests.TestClass");
                Assert.fail("shouldn't be able to load the test class");
            } catch (ClassNotFoundException ok) {
                //
            }
        } finally {
            IoUtils.safeClose(cl);
        }

        ZipFile file = null;
        try {
            file = new ZipFile(jar);
            Assert.fail("should not be able to open" + jar);
        } catch (ZipException expected) {
            // ok
            return;
        } finally {
            IoUtils.safeClose(file);
        }
    }

    private File getBackupFile(File file) {
        String fileName = file.getName();
        if (fileName.endsWith(JAR_EXT)) {
            return new File(file.getParentFile(), fileName.substring(0, fileName.length() - JAR_EXT.length()) + BACKUP_EXT);
        }
        return file;
    }
}
