/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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
package org.jboss.as.controller;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;

/**
 * Simple add handler which, if allowed, restarts a parent resource when the child is added.
 * Otherwise the server is put into a forced reload.
 *
 * @author Jason T. Greene
 */
public abstract class RestartParentResourceAddHandler extends RestartParentResourceHandlerBase implements OperationDescriptor {

    protected final Collection<? extends AttributeDefinition> attributes;

    protected RestartParentResourceAddHandler(String parentKeyName) {
        this(parentKeyName, List.of());
    }

    protected RestartParentResourceAddHandler(String parentKeyName, Collection<? extends AttributeDefinition> attributes) {
        super(parentKeyName);
        this.attributes = attributes.isEmpty() ? List.of() : List.copyOf(attributes);
    }

    @Deprecated(forRemoval = true)
    protected RestartParentResourceAddHandler(String parentKeyName, RuntimeCapability ... capabilities) {
        this(parentKeyName);
    }

    @Deprecated(forRemoval = true)
    public RestartParentResourceAddHandler(String parentKeyName, Set<RuntimeCapability> capabilities, Collection<? extends AttributeDefinition> attributes) {
        this(parentKeyName, attributes);
    }

    @Override
    public Collection<? extends AttributeDefinition> getAttributes() {
        return this.attributes;
    }

    @Override
    protected void updateModel(OperationContext context, ModelNode operation) throws OperationFailedException {
        final Resource resource = context.createResource(PathAddress.EMPTY_ADDRESS);
        populateModel(operation, resource.getModel());
        recordCapabilitiesAndRequirements(context, operation, resource);
    }

    protected void recordCapabilitiesAndRequirements(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
        for (RuntimeCapability<?> capability : context.getResourceRegistration().getCapabilities()) {
            if (capability.isDynamicallyNamed()) {
                context.registerCapability(capability.fromBaseCapability(context.getCurrentAddress()));
            } else {
                context.registerCapability(capability);
            }
        }

        ModelNode model = resource.getModel();
        for (AttributeDefinition ad : attributes) {
            if (model.hasDefined(ad.getName()) || ad.hasCapabilityRequirements()) {
                ad.addCapabilityRequirements(context, resource, model.get(ad.getName()));
            }
        }
    }

    /**
     * Populate the given node in the persistent configuration model based on the values in the given operation.
     *
     * @param operation the operation
     * @param model persistent configuration model node that corresponds to the address of {@code operation}
     *
     * @throws OperationFailedException if {@code operation} is invalid or populating the model otherwise fails
     */
    protected void populateModel(final ModelNode operation, final ModelNode model) throws OperationFailedException {
        for (AttributeDefinition attribute : this.attributes) {
            attribute.validateAndSet(operation, model);
        }
    }
}
