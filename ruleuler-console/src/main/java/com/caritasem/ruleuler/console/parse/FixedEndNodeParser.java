package com.caritasem.ruleuler.console.parse;

import org.dom4j.Element;
import com.bstek.urule.model.flow.EndNode;
import com.bstek.urule.parse.flow.EndNodeParser;

/**
 * 修复EndNodeParser未解析x/y/width/height的bug
 */
public class FixedEndNodeParser extends EndNodeParser {
    @Override
    public EndNode parse(Element element) {
        EndNode end = super.parse(element);
        end.setX(element.attributeValue("x"));
        end.setY(element.attributeValue("y"));
        end.setWidth(element.attributeValue("width"));
        end.setHeight(element.attributeValue("height"));
        return end;
    }
}
