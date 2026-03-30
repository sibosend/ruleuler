/**
 * Created by Jacky.gao on 2016/5/24.
 * Modified: 暴露 uruleConsole API 给 parent，隐藏内部 tab 栏
 */
import React,{Component} from 'react';
import QuickStart from '../../../frame/QuickStart.js';
import IFrame from './IFrame.jsx';
import * as event from '../../componentEvent.js';
import * as action from '../../../frame/action.js';
import {nextIFrameId} from '../../../Utils.js';

export default class FrameTab extends Component{
    constructor(props){
        super(props);
        this.state={data:[], activeFullPath: null};
    }

    _processFullPath(fullPath){
        fullPath=fullPath.replace(new RegExp('/','gm'),'');
        fullPath=fullPath.replace(new RegExp('\\.','gm'),'');
        fullPath=fullPath.replace(new RegExp(':','gm'),'');
        return fullPath;
    }

    _buildTabLabel(item){
        const fileName = item.name;
        const pointPos = fileName.indexOf('.');
        const fileType = fileName.substring(pointPos+1, fileName.length);
        let type = '';
        if(fileType==='推送客户端配置'){
            type='>>'+item.project;
        }else if(fileType==='资源权限配置' || fileType==='客户端访问权限配置'){
            type='AUTH';
        }else{
            type=action.buildType(fileType);
        }
        if(type==='package'){
            type=item.fullPath.substring(1, item.fullPath.length);
        }
        return (type==='AUTH') ? fileName : type+':'+fileName;
    }

    _notifyParent(){
        const bridge = window.parent && window.parent.uruleTabBridge;
        if(!bridge) return;
        const tabs = this.state.data.map(item => ({
            key: item.fullPath,
            label: this._buildTabLabel(item),
            closable: true,
            project: item.project || ((item.fullPath || '').split('/').filter(Boolean)[0]) || null,
        }));
        bridge.onTabsChange(tabs, this.state.activeFullPath);
    }

    addTab(newTabData){
        let data=this.state.data, exist=false, fullPath=this._processFullPath(newTabData.fullPath);
        for(let item of data){
            if(this._processFullPath(item.fullPath)===fullPath){
                exist=true;
            }
        }
        if(!exist){
            newTabData._iframeId = nextIFrameId();
            data.push(newTabData);
        }
        this.setState({data, activeFullPath: newTabData.fullPath}, () => {
            this._showPane(newTabData.fullPath);
            this._notifyParent();
        });
    }

    switchTab(fullPath){
        this.setState({activeFullPath: fullPath}, () => {
            this._showPane(fullPath);
            this._notifyParent();
        });
    }

    closeTab(fullPath){
        const data = this.state.data;
        const processedTarget = this._processFullPath(fullPath);
        const idx = data.findIndex(item => this._processFullPath(item.fullPath) === processedTarget);
        if(idx === -1) return false;

        const item = data[idx];
        const iframeId = item._iframeId;
        const frame = iframeId && $(`#${iframeId}`).get(0);
        if(frame && frame.contentWindow && frame.contentWindow._dirty){
            const result = confirm('当前页面内容未保存，确实要关闭吗？');
            if(!result) return false;
        }

        data.splice(idx, 1);
        let newActive = this.state.activeFullPath;
        if(this._processFullPath(newActive) === processedTarget){
            if(data.length > 0){
                const newIdx = Math.min(idx, data.length - 1);
                newActive = data[newIdx].fullPath;
            } else {
                newActive = null;
            }
        }
        this.setState({data, activeFullPath: newActive}, () => {
            if(newActive) this._showPane(newActive);
            this._notifyParent();
        });
        return true;
    }

    closeAllTabs(){
        this.setState({data: [], activeFullPath: null}, () => this._notifyParent());
    }

    closeOtherTabs(fullPath){
        const processedTarget = this._processFullPath(fullPath);
        const keep = this.state.data.filter(item => this._processFullPath(item.fullPath) === processedTarget);
        this.setState({data: keep, activeFullPath: fullPath}, () => {
            this._showPane(fullPath);
            this._notifyParent();
        });
    }

    _showPane(fullPath){
        const processed = this._processFullPath(fullPath);
        // 隐藏所有 pane，显示目标
        $('.tab-pane').removeClass('active in');
        $('#iframeTab-'+processed).addClass('active in');
    }

    /** 根据 fullPath 构建编辑器 URL 并打开文件 tab */
    openFile(fullPath){
        const fileName = fullPath.substring(fullPath.lastIndexOf('/') + 1);
        const dotPos = fileName.indexOf('.') + 1;
        const extName = fileName.substring(dotPos);
        const editorMap = {
            'rs.xml': '/ruleseteditor',
            'rea.xml': '/reaeditor',
            'dt.xml': '/decisiontableeditor',
            'dts.xml': '/scriptdecisiontableeditor',
            'dtree.xml': '/decisiontreeeditor',
            'rl.xml': '/ruleflowdesigner',
            'ul': '/uleditor',
            'sc': '/scorecardeditor',
            'vl.xml': '/variableeditor',
            'cl.xml': '/constanteditor',
            'pl.xml': '/parametereditor',
            'al.xml': '/actioneditor',
        };
        const editorPath = editorMap[extName];
        if(!editorPath) return;
        const url = window._server + editorPath + '?file=' + fullPath;
        this.addTab({
            id: fullPath,
            name: fileName,
            fullPath: fullPath,
            path: url,
            active: true,
        });
    }

    componentDidMount(){
        // 暴露 API 给 parent
        window.uruleConsole = {
            switchTab: (fullPath) => this.switchTab(fullPath),
            openFile: (fullPath) => this.openFile(fullPath),
            closeTab: (fullPath) => this.closeTab(fullPath),
            closeAllTabs: () => this.closeAllTabs(),
            closeOtherTabs: (fullPath) => this.closeOtherTabs(fullPath),
            getTabs: () => this.state.data.map(item => ({
                key: item.fullPath,
                label: this._buildTabLabel(item),
            })),
            getActiveTab: () => this.state.activeFullPath,
            hasTab: (fullPath) => {
                const processed = this._processFullPath(fullPath);
                return this.state.data.some(item => this._processFullPath(item.fullPath) === processed);
            },
        };

        event.eventEmitter.on(event.TREE_NODE_CLICK,(data)=>{
           this.addTab(data);
        });

        // 通知 parent iframe 已就绪
        const bridge = window.parent && window.parent.uruleTabBridge;
        if(bridge && bridge.onReady) bridge.onReady();
    }

    componentWillUnmount(){
        event.eventEmitter.removeAllListeners(event.TREE_NODE_CLICK);
        delete window.uruleConsole;
    }

    render(){
        const {data, activeFullPath} = this.state;
        const {welcomePage} = this.props;

        if(data.length === 0){
            return (<div/>);
        }

        const activeProcessed = activeFullPath ? this._processFullPath(activeFullPath) : null;

        return (
            <div>
                {/* tab 栏隐藏，由外层 AdminLayout 管理 */}
                <div style={{display:'none'}}>
                    <ul className="nav nav-tabs" id='fornavframetab_' style={{fontSize:"12px"}}></ul>
                </div>
                <div className="tab-content">
                    {data.map((item) => {
                        const processed = this._processFullPath(item.fullPath);
                        const isActive = processed === activeProcessed;
                        return (
                            <div className={'tab-pane' + (isActive ? ' active in' : '')}
                                 id={'iframeTab-'+processed}
                                 key={'key'+processed}>
                                <IFrame id={item._iframeId} path={item.path}/>
                            </div>
                        );
                    })}
                </div>
            </div>
        );
    }
}
