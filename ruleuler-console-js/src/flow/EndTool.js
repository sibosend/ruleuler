import BaseTool from './BaseTool.js';
import EndNode from './EndNode.js';

export default class EndTool extends BaseTool{
    getType(){
        return '结束';
    }
    getIcon(){
        return `<i class="rf rf-start" style="color:#e74c3c"></i>`
    }
    newNode(){
        return new EndNode();
    }
    getConfigs(){
        return {
            in:-1,
            out:0,
            single:true
        };
    }
    getPropertiesProducer(){
        const _this=this;
        return function (){
            const g=$(`<div></div>`);
            g.append(_this.getCommonProperties(this));
            return g;
        }
    }
}
