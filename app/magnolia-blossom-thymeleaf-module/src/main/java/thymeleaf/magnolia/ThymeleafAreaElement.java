package thymeleaf.magnolia;

import info.magnolia.cms.beans.config.ServerConfiguration;
import info.magnolia.cms.core.MgnlNodeType;
import info.magnolia.jcr.RuntimeRepositoryException;
import info.magnolia.jcr.util.ContentMap;
import info.magnolia.jcr.util.NodeUtil;
import info.magnolia.rendering.context.RenderingContext;
import info.magnolia.rendering.engine.RenderException;
import info.magnolia.rendering.engine.RenderingEngine;
import info.magnolia.rendering.template.AreaDefinition;
import info.magnolia.templating.elements.AreaElement;
import info.magnolia.templating.inheritance.DefaultInheritanceContentDecorator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.thymeleaf.exceptions.TemplateProcessingException;

/**
 * Created with IntelliJ IDEA. User: Thomas Date: 11.11.12 Time: 16:11 To change this template use File | Settings |
 * File Templates.
 */
public class ThymeleafAreaElement extends AreaElement {

    private Node areaNode;
    private AreaDefinition areaDefinition;
    private String type;
    private Object areaPath2;

    public ThymeleafAreaElement(final ServerConfiguration server, final RenderingContext renderingContext,
            final RenderingEngine renderingEngine) {
        super(server, renderingContext, renderingEngine);
    }

    private String resolveType() {
        return type != null ? type : areaDefinition != null && areaDefinition.getType() != null ? areaDefinition
                .getType() : AreaDefinition.DEFAULT_TYPE;
    }

    public Map<String, Object> getContextMap() {

        try {
            this.areaNode = getPassedContent();
            if (this.areaNode != null) {
                this.areaPath2 = getNodePath(areaNode);
            } else {
                // will be null if no area has been created (for instance for optional areas)
                // current content is the parent node
                final Node parentNode = currentContent();
                this.areaNode = tryToCreateAreaNode(parentNode);
                this.areaPath2 = getNodePath(parentNode) + "/" + getName();
            }
            areaDefinition = resolveAreaDefinition();
            type = resolveType();

            if (isInherit() && areaNode != null) {
                try {
                    areaNode = new DefaultInheritanceContentDecorator(areaNode, areaDefinition.getInheritance())
                            .wrapNode(areaNode);
                } catch (final RepositoryException e) {
                    throw new RuntimeRepositoryException(e);
                }
            }
            final Map<String, Object> contextObjects = new HashMap<>();

            final List<ContentMap> components = new ArrayList<>();

            if (areaNode != null) {
                for (final Node node : NodeUtil.getNodes(areaNode, MgnlNodeType.NT_COMPONENT)) {
                    components.add(new ContentMap(node));
                }
            }
            if (AreaDefinition.TYPE_SINGLE.equals(type)) {
                if (components.size() > 1) {
                    throw new RenderException("Can't render single area [" + areaNode
                            + "]: expected one component node but found more.");
                }
                if (components.size() == 1) {
                    contextObjects.put(ATTRIBUTE_COMPONENT, components.get(0));
                } else {
                    contextObjects.put(ATTRIBUTE_COMPONENT, null);
                }
            } else {
                contextObjects.put(ATTRIBUTE_COMPONENTS, components);
            }
            return contextObjects;
        } catch (final Exception x) {
            throw new TemplateProcessingException("Createing context map", x);
        }
    }
}
