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

package org.jboss.as.core.model.test.access;

import static org.jboss.as.controller.PathAddress.pathAddress;
import static org.jboss.as.controller.PathElement.pathElement;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.AUTHORIZATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.BASE_ROLE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOSTS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST_SCOPED_ROLE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_HEADERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RECURSIVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.jboss.as.controller.access.constraint.ApplicationTypeConfig;
import org.jboss.as.controller.access.constraint.SensitivityClassification;
import org.jboss.as.controller.access.management.ApplicationTypeAccessConstraintDefinition;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.access.rbac.StandardRole;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.core.model.test.AbstractCoreModelTest;
import org.jboss.as.core.model.test.KernelServices;
import org.jboss.as.core.model.test.TestModelType;
import org.jboss.dmr.ModelNode;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Ladislav Thon <lthon@redhat.com>
 */
public class HostScopedRolesTestCase extends AbstractCoreModelTest {
    private static final String FOO = "foo";

    private static final String MONITOR = "Monitor"; // StandardRole.MONITOR
    private static final String OPERATOR = "Operator"; // StandardRole.OPERATOR

    private static final String SOME_HOST = "some-host";
    private static final String ANOTHER_HOST = "another-host";

    private KernelServices kernelServices;

    @Before
    public void setUp() throws Exception {
        // must initialize the classes, otherwise the kernel won't boot correctly
        new SensitiveTargetAccessConstraintDefinition(new SensitivityClassification("play", "security-realm", true, true, true));
        new ApplicationTypeAccessConstraintDefinition(new ApplicationTypeConfig("play", "deployment", false));

        kernelServices = createKernelServicesBuilder(TestModelType.DOMAIN)
                .setXmlResource("domain-all.xml")
                .validateDescription()
                .build();
    }

    @Test
    public void testReadHostScopedRole() {
        assertTrue(kernelServices.isSuccessfulBoot());

        // see domain-all.xml

        ModelNode operation = Util.createOperation(READ_RESOURCE_OPERATION, pathAddress(
                pathElement(CORE_SERVICE, MANAGEMENT),
                pathElement(ACCESS, AUTHORIZATION),
                pathElement(HOST_SCOPED_ROLE, "c")
        ));
        operation.get(RECURSIVE).set(true);
        ModelNode result = execute(operation);
        checkOutcome(result);
        result = result.get(RESULT);

        assertEquals("Deployer", result.get(BASE_ROLE).asString());
        assertEquals(2, result.get(HOSTS).asList().size());
        assertEquals("primary", result.get(HOSTS).get(0).asString());
        assertEquals("secondary", result.get(HOSTS).get(1).asString());

        operation = Util.createOperation(READ_RESOURCE_OPERATION, pathAddress(
                pathElement(CORE_SERVICE, MANAGEMENT),
                pathElement(ACCESS, AUTHORIZATION),
                pathElement(HOST_SCOPED_ROLE, "d")
        ));
        operation.get(RECURSIVE).set(true);
        result = execute(operation);
        checkOutcome(result);
        result = result.get(RESULT);

        assertEquals("Administrator", result.get(BASE_ROLE).asString());
        assertFalse(result.get(HOSTS).isDefined());
    }

    @Test
    public void testAddHostScopedRoleWithHosts() {
        assertTrue(kernelServices.isSuccessfulBoot());

        ModelNode operation = Util.createOperation(ADD, pathAddress(
                pathElement(CORE_SERVICE, MANAGEMENT),
                pathElement(ACCESS, AUTHORIZATION),
                pathElement(HOST_SCOPED_ROLE, FOO)
        ));
        operation.get(BASE_ROLE).set(MONITOR);
        operation.get(HOSTS).add(SOME_HOST);
        operation.get(HOSTS).add(ANOTHER_HOST);
        ModelNode result = execute(operation);
        checkOutcome(result);

        operation = Util.createOperation(READ_RESOURCE_OPERATION, pathAddress(
                pathElement(CORE_SERVICE, MANAGEMENT),
                pathElement(ACCESS, AUTHORIZATION),
                pathElement(HOST_SCOPED_ROLE, FOO)
        ));
        operation.get(RECURSIVE).set(true);
        result = execute(operation);
        checkOutcome(result);
        result = result.get(RESULT);

        assertEquals(MONITOR, result.get(BASE_ROLE).asString());
        assertEquals(2, result.get(HOSTS).asList().size());
        assertEquals(SOME_HOST, result.get(HOSTS).get(0).asString());
        assertEquals(ANOTHER_HOST, result.get(HOSTS).get(1).asString());
    }

    @Test
    public void testAddHostScopedRoleWithoutHosts() {
        assertTrue(kernelServices.isSuccessfulBoot());

        ModelNode operation = Util.createOperation(ADD, pathAddress(
                pathElement(CORE_SERVICE, MANAGEMENT),
                pathElement(ACCESS, AUTHORIZATION),
                pathElement(HOST_SCOPED_ROLE, FOO)
        ));
        operation.get(BASE_ROLE).set(MONITOR);
        ModelNode result = execute(operation);
        checkOutcome(result);

        operation = Util.createOperation(READ_RESOURCE_OPERATION, pathAddress(
                pathElement(CORE_SERVICE, MANAGEMENT),
                pathElement(ACCESS, AUTHORIZATION),
                pathElement(HOST_SCOPED_ROLE, FOO)
        ));
        operation.get(RECURSIVE).set(true);
        result = execute(operation);
        checkOutcome(result);
        result = result.get(RESULT);

        assertEquals(MONITOR, result.get(BASE_ROLE).asString());
        assertFalse(result.hasDefined(HOSTS));
    }

    @Test
    public void testModifyBaseRoleOfHostScopedRole() {
        testAddHostScopedRoleWithHosts();

        ModelNode operation = Util.createOperation(WRITE_ATTRIBUTE_OPERATION, pathAddress(
                pathElement(CORE_SERVICE, MANAGEMENT),
                pathElement(ACCESS, AUTHORIZATION),
                pathElement(HOST_SCOPED_ROLE, FOO)
        ));
        operation.get(NAME).set(BASE_ROLE);
        operation.get(VALUE).set(OPERATOR);
        ModelNode result = execute(operation);
        checkOutcome(result);

        operation = Util.createOperation(READ_RESOURCE_OPERATION, pathAddress(
                pathElement(CORE_SERVICE, MANAGEMENT),
                pathElement(ACCESS, AUTHORIZATION),
                pathElement(HOST_SCOPED_ROLE, FOO)
        ));
        operation.get(RECURSIVE).set(true);
        result = execute(operation);
        checkOutcome(result);
        result = result.get(RESULT);

        assertEquals(OPERATOR, result.get(BASE_ROLE).asString());
        assertEquals(2, result.get(HOSTS).asList().size());
        assertEquals(SOME_HOST, result.get(HOSTS).get(0).asString());
        assertEquals(ANOTHER_HOST, result.get(HOSTS).get(1).asString());
    }

    @Test
    public void testModifyHostsOfHostScopedRole() {
        testAddHostScopedRoleWithHosts();

        ModelNode operation = Util.createOperation(WRITE_ATTRIBUTE_OPERATION, pathAddress(
                pathElement(CORE_SERVICE, MANAGEMENT),
                pathElement(ACCESS, AUTHORIZATION),
                pathElement(HOST_SCOPED_ROLE, FOO)
        ));
        operation.get(NAME).set(HOSTS);
        operation.get(VALUE).add(SOME_HOST);
        ModelNode result = execute(operation);
        checkOutcome(result);

        operation = Util.createOperation(READ_RESOURCE_OPERATION, pathAddress(
                pathElement(CORE_SERVICE, MANAGEMENT),
                pathElement(ACCESS, AUTHORIZATION),
                pathElement(HOST_SCOPED_ROLE, FOO)
        ));
        operation.get(RECURSIVE).set(true);
        result = execute(operation);
        checkOutcome(result);
        result = result.get(RESULT);

        assertEquals(MONITOR, result.get(BASE_ROLE).asString());
        assertEquals(1, result.get(HOSTS).asList().size());
        assertEquals(SOME_HOST, result.get(HOSTS).get(0).asString());
    }

    @Test
    public void testRemoveHostsOfHostScopedRole() {
        testAddHostScopedRoleWithHosts();

        ModelNode operation = Util.createOperation(WRITE_ATTRIBUTE_OPERATION, pathAddress(
                pathElement(CORE_SERVICE, MANAGEMENT),
                pathElement(ACCESS, AUTHORIZATION),
                pathElement(HOST_SCOPED_ROLE, FOO)
        ));
        operation.get(NAME).set(HOSTS); // no operation.get(VALUE).set(...), meaning "undefined"
        ModelNode result = execute(operation);
        checkOutcome(result);

        operation = Util.createOperation(READ_RESOURCE_OPERATION, pathAddress(
                pathElement(CORE_SERVICE, MANAGEMENT),
                pathElement(ACCESS, AUTHORIZATION),
                pathElement(HOST_SCOPED_ROLE, FOO)
        ));
        operation.get(RECURSIVE).set(true);
        result = execute(operation);
        checkOutcome(result);
        result = result.get(RESULT);

        assertEquals(MONITOR, result.get(BASE_ROLE).asString());
        assertFalse(result.hasDefined(HOSTS));
    }

    @Test
    public void testRemoveHostScopedRole() {
        testAddHostScopedRoleWithHosts();

        ModelNode operation = Util.createOperation(REMOVE, pathAddress(
                pathElement(CORE_SERVICE, MANAGEMENT),
                pathElement(ACCESS, AUTHORIZATION),
                pathElement(HOST_SCOPED_ROLE, FOO)
        ));
        ModelNode result = execute(operation);
        checkOutcome(result);

        operation = Util.createOperation(READ_RESOURCE_OPERATION, pathAddress(
                pathElement(CORE_SERVICE, MANAGEMENT),
                pathElement(ACCESS, AUTHORIZATION),
                pathElement(HOST_SCOPED_ROLE)
        ));
        operation.get(RECURSIVE).set(true);
        result = execute(operation);
        checkOutcome(result);
        result = result.get(RESULT);

        assertEquals(2, result.asList().size()); // see domain-all.xml
    }

    // test utils

    private ModelNode execute(ModelNode operation) {
        operation.get(OPERATION_HEADERS, "roles").add(StandardRole.SUPERUSER.name());
        return kernelServices.executeOperation(operation);
    }
}
