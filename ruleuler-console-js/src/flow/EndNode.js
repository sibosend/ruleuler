import endSVG from './svg/end.svg';
import BaseNode from './BaseNode.js';

export default class EndNode extends BaseNode{
    getSvgIcon(){
        return endSVG;
    }
    toXML(){
        const json=this.toJSON();
        json.type="EndNode";
        const nodeName=this.getNodeName(json.type);
        const nodeProps=this.getXMLNodeBaseProps(json);
        let xml=`<${nodeName} ${nodeProps}/>`;
        return xml;
    }
}
