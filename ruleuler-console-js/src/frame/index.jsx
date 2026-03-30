/**
 * Created by Jacky.gao on 2016/5/23.
 */
import '../css/iconfont.css';
import '../../node_modules/bootstrap/dist/css/bootstrap.css';
import '../../node_modules/codemirror/lib/codemirror.css';
import '../../node_modules/bootstrapvalidator/dist/css/bootstrapValidator.css';
import '../bootstrap-contextmenu.js';
import React from 'react';
import ReactDOM,{} from 'react-dom';
import {createStore,applyMiddleware} from 'redux';
import {Provider} from 'react-redux';
import * as ACTIONS from './action.js';
import reducer from './reducer.js';
import thunk from 'redux-thunk';
import Tree from '../components/tree/component/Tree.jsx';
import Splitter from '../components/splitter/component/Splitter.jsx';
import FrameTab from '../components/frametab/component/FrameTab.jsx';
import ComponentContainer from './components/ComponentContainer.jsx';
import * as event from './event.js';
import * as componentEvent from '../components/componentEvent.js';
import Loading from '../components/loading/component/Loading.jsx';

// 环境水印
function createWatermark() {
    // 优先使用浏览器地址栏的 URL，其次才是 window._server
    const currentUrl = window.location.href;
    const server = window._server || '';
    const checkStr = currentUrl + ' ' + server; // 同时检查 URL 和 server

    let env = '未知环境';
    let color = 'rgba(0,0,0,0.08)';

    if (checkStr.includes('localhost') || checkStr.includes('127.0.0.1') || checkStr.includes(':16')) {
        env = '开发环境 DEV';
        color = 'rgba(0,128,0,0.10)';
    } else if (checkStr.includes('test') || checkStr.includes('uat') || checkStr.includes('pre')) {
        env = '测试环境 TEST';
        color = 'rgba(255,165,0,0.12)';
    } else if (checkStr.includes('prod')) {
        env = '生产环境 PROD';
        color = 'rgba(255,0,0,0.08)';
    }
    
    const watermark = document.createElement('div');
    watermark.style.cssText = `
        position: fixed;
        top: 0; left: 0;
        width: 100%; height: 100%;
        font-size: 120px;
        font-weight: bold;
        color: ${color};
        transform: rotate(-30deg);
        pointer-events: none;
        z-index: 9999;
        user-select: none;
        display: flex;
        flex-wrap: wrap;
        justify-content: space-around;
        align-content: space-around;
        gap: 150px;
    `;

    // 重复显示多个水印
    for (let i = 0; i < 6; i++) {
        const text = document.createElement('span');
        text.textContent = env;
        watermark.appendChild(text);
    }

    document.body.appendChild(watermark);
}

$(document).ready(function () {
    createWatermark();
    // 解析 URL 参数，检测单项目模式
    const urlParams = new URLSearchParams(window.location.search);
    const singleProjectName = urlParams.get('projectName');
    window._singleProjectMode = !!singleProjectName;

    window._types=null,window._projectName=singleProjectName||null,window.componentEvent=componentEvent;
    const store=createStore(reducer,applyMiddleware(thunk));
    if(singleProjectName){
        store.dispatch(ACTIONS.loadData(undefined, singleProjectName));
    }else{
        store.dispatch(ACTIONS.loadData());
    }
    const documentHeight=$(document).height()+'px';
    event.eventEmitter.on(event.CHANGE_CLASSIFY,classify=>{
        window._classify=classify;
        if(classify){
            $('#__classify_display').html('<i class="rf rf-check"></i> 分类展示');
            $('#__no_classify_display').html('&nbsp;&nbsp;&nbsp;&nbsp;集中展示');
        }else{
            $('#__classify_display').html('&nbsp;&nbsp;&nbsp;&nbsp;分类展示');
            $('#__no_classify_display').html('<i class="rf rf-check"></i> 集中展示');
        }
    });
    event.eventEmitter.on(event.PROJECT_LIST_CHANGE,projectNames=>{
        const menu=$('#__project_filter_menu');
        const menuChildren=menu.children('li');
        menuChildren.each(function(index,li){
            const $li=$(li);
            if(!$li.hasClass('_firstItem')){
                $li.remove();
            }else{
                $li.find('a').css("margin-left",'0px');
            }
        });
        $('#_show_all_projects_i').addClass('rf rf-check');
        for(let name of projectNames){
            const newLi=$(`<li class="p_${name}"></li>`),link=$(`<a href="###" style="margin-left: 22px"><i></i> ${name}</a>`);
            newLi.append(link);
            menu.append(newLi);

            link.click(function(){
                if(!window._singleProjectMode) window._projectName=name;
                componentEvent.eventEmitter.emit(componentEvent.SHOW_LOADING);
                setTimeout(function() {
                    store.dispatch(ACTIONS.loadData(window._classify,window._projectName,window._types,window.searchFileName));
                    event.eventEmitter.emit(event.PROJECT_FILTER_CHANGE,name);
                    componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
                },200);
            });
        }
    });
    event.eventEmitter.on(event.PROJECT_FILTER_CHANGE,name=>{
        const menu=$('#__project_filter_menu');
        const menuChildren=menu.children('li');
        menuChildren.each(function(index,li){
            $(li).find('i').removeClass('rf rf-check');
            $(li).find('a').css('margin-left','22px');
        });
        const li=menu.find(`.p_${name}`);
        li.find('a').css('margin-left','0px');
        li.find('i').addClass('rf rf-check');
    });
    function searchFile(){
        const searchFileName=$('.fileSearchText').val();
        window.searchFileName=searchFileName;
        store.dispatch(ACTIONS.loadData(window._classify,window._projectName,window._types,window.searchFileName));
    }
    ReactDOM.render(
        <div >
            <Loading show={true}/>
            <Provider store={store}>
                <Splitter orientation='vertical' position='20%'>
                    <div>
                        <div id="__toolbar_container" style={{border:'solid 1px #ddd',height:'35px',background:'#f5f5f5',padding:'5px 10px'}}>
                            <span className="dropdown" style={{margin:'5px'}}>
                                <a href="###" className="dropdown-toggle" data-toggle="dropdown" title="知识库内容展示方式"><i className="rf rf-display" style={{fontSize:'12pt'}}></i> <b className="caret"></b></a>
                                <ul className="dropdown-menu">
                                    <li><a href="###" id="__classify_display" onClick={()=>{
                                        componentEvent.eventEmitter.emit(componentEvent.SHOW_LOADING);
                                        setTimeout(function() {
                                            store.dispatch(ACTIONS.loadData(true,window._projectName,window._types));
                                            componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
                                        },200);
                                    }}>✔&nbsp;分类展示</a></li>
                                    <li><a href="###" id="__no_classify_display" onClick={()=>{
                                        componentEvent.eventEmitter.emit(componentEvent.SHOW_LOADING);
                                        setTimeout(function() {
                                            store.dispatch(ACTIONS.loadData(false,window._projectName,window._types));
                                            componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
                                        },200);
                                    }}>&nbsp;&nbsp;&nbsp;&nbsp;集中展示</a></li>
                                </ul>
                            </span>

                            <span className="dropdown" style={{margin:'5px'}}>
                                <a href="###" className="dropdown-toggle" data-toggle="dropdown" title="项目过滤"><i className="rf rf-list" style={{fontSize:'12pt'}}></i> <b className="caret"></b></a>
                                <ul className="dropdown-menu" id="__project_filter_menu">
                                    <li className="_firstItem">
                                        <a href="###" onClick={function(e){
                                                componentEvent.eventEmitter.emit(componentEvent.SHOW_LOADING);
                                                setTimeout(function() {
                                                        store.dispatch(ACTIONS.loadData(window._classify,window._projectName,window._types));
                                                        componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
                                                },200);
                                                const menu=$('#__project_filter_menu');
                                                const menuChildren=menu.children('li');
                                                menuChildren.each(function(index,li){
                                                    $(li).find('i').removeClass('rf rf-check');
                                                    $(li).find('a').css('margin-left','22px');
                                                });
                                                $(this).css('margin-left','0px');
                                                $('#_show_all_projects_i').addClass('rf rf-check');
                                                if(!window._singleProjectMode) window._projectName=null;
                                            }}><i id="_show_all_projects_i"></i> 显示所有项目
                                        </a>
                                    </li>
                                </ul>
                            </span>

                            <span className="dropdown" style={{margin:'5px'}}>
                                <a href="###" className="dropdown-toggle" data-toggle="dropdown" title="文件类型过滤"><i className="rf rf-type" style={{fontSize:'12pt'}}></i> <b className="caret"></b></a>
                                <ul className="dropdown-menu">
                                    <li><a href="###" onClick={function(e){
                                        componentEvent.eventEmitter.emit(componentEvent.SHOW_LOADING);
                                        window._types='all';
                                        setTimeout(function() {
                                            store.dispatch(ACTIONS.loadData(window._classify,window._projectName,window._types));
                                            componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
                                        },200);
                                        $('.filter_file').removeClass('rf rf-check');
                                        $(e.target).children('.filter_file').addClass('rf rf-check');
                                    }}><i className="filter_file rf rf-check"></i> <i className="glyphicon glyphicon-th"></i> 显示所有文件</a></li>
                                    <li>
                                        <a href="###" onClick={function(e){
                                            componentEvent.eventEmitter.emit(componentEvent.SHOW_LOADING);
                                            window._types='lib';
                                            setTimeout(function() {
                                                store.dispatch(ACTIONS.loadData(window._classify,window._projectName,window._types));
                                                componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
                                            },200);
                                            $('.filter_file').removeClass('rf rf-check');
                                            $(e.target).children('.filter_file').addClass('rf rf-check');
                                        }}><i className="filter_file"></i>  <i className="rf rf-library"></i> 库文件</a>
                                    </li>
                                    <li>
                                        <a href="###" onClick={function(e){
                                            componentEvent.eventEmitter.emit(componentEvent.SHOW_LOADING);
                                            window._types='rule';
                                            setTimeout(function() {
                                                store.dispatch(ACTIONS.loadData(window._classify,window._projectName,window._types));
                                                componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
                                            },200);
                                            $('.filter_file').removeClass('rf rf-check');
                                            $(e.target).children('.filter_file').addClass('rf rf-check');
                                        }}><i className="filter_file"></i>  <i className="rf rf-rule"></i> 决策集</a>
                                    </li>
                                    <li>
                                        <a href="###" onClick={function(e){
                                            componentEvent.eventEmitter.emit(componentEvent.SHOW_LOADING);
                                            window._types='table';
                                            setTimeout(function() {
                                                store.dispatch(ACTIONS.loadData(window._classify,window._projectName,window._types));
                                                componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
                                            },200);
                                            $('.filter_file').removeClass('rf rf-check');
                                            $(e.target).children('.filter_file').addClass('rf rf-check');
                                        }}><i className="filter_file"></i> <i className="rf rf-table"></i> 决策表</a>
                                    </li>
                                    <li>
                                        <a href="###" onClick={function(e){
                                            componentEvent.eventEmitter.emit(componentEvent.SHOW_LOADING);
                                            window._types='tree';
                                            setTimeout(function() {
                                                store.dispatch(ACTIONS.loadData(window._classify,window._projectName,window._types));
                                                componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
                                            },200);
                                            $('.filter_file').removeClass('rf rf-check');
                                            $(e.target).children('.filter_file').addClass('rf rf-check');
                                        }}><i className="filter_file"></i> <i className="rf rf-tree"></i> 决策树</a>
                                    </li>
                                    <li>
                                        <a href="###" onClick={function(e){
                                            componentEvent.eventEmitter.emit(componentEvent.SHOW_LOADING);
                                            window._types='flow';
                                            setTimeout(function() {
                                                store.dispatch(ACTIONS.loadData(window._classify,window._projectName,window._types));
                                                componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
                                            },200);
                                            $('.filter_file').removeClass('rf rf-check');
                                            $(e.target).children('.filter_file').addClass('rf rf-check');
                                        }}><i className="filter_file"></i> <i className="rf rf-flow"></i> 决策流</a>
                                    </li>
                                </ul>
                            </span>

                            <span className="dropdown" style={{margin:'5px'}}>
                                <a href="###" className="dropdown-toggle" data-toggle="dropdown" title="权限配置"><i className="rf rf-authority" style={{fontSize:'12pt'}}></i> <b className="caret"></b></a>
                                <ul className="dropdown-menu" id="__authority_config_menu">
                                    <li>
                                        <a href="###" onClick={function(e){
                                                const url=window._server+'/permission';
                                                componentEvent.eventEmitter.emit(componentEvent.TREE_NODE_CLICK,{
                                                    id:'security_config_',
                                                    name:'资源权限配置',
                                                    fullPath:'security_config_',
                                                    path:url
                                                });
                                            }}><i></i> 资源权限配置
                                        </a>
                                    </li>
                                </ul>
                            </span>
                        </div>
                        <div className='tree' style={{marginLeft:'10px'}}>
                            <div style={{margin:'10px 0px 5px 2px'}}>
                                <input type="text" className="form-control fileSearchText" placeholder="输入要查询的文件名..." style={{display:'inline-block',width:'170px'}}></input>
                                <a href="###" onClick={searchFile} style={{margin:'6px',fontSize:'16px'}}><i className="glyphicon glyphicon-search"></i></a>
                            </div>
                            <Tree draggable={true}/>
                        </div>
                    </div>
                    <div>
                        <ComponentContainer/>
                        <FrameTab welcomePage={window._welcomePage}/>
                    </div>
                </Splitter>
            </Provider>
        </div>,
        document.getElementById("container")
    );

    // 单项目模式：隐藏左上角工具栏和项目过滤，自动展开所有树节点
    if(window._singleProjectMode){
        $('#__toolbar_container').hide();
        // 数据加载完后自动展开所有树节点
        setTimeout(function(){
            $('.tree li.parent_li > ul > li').show();
            $('.tree li.parent_li > span > i.rf-plus').addClass('rf-minus').removeClass('rf-plus');
        }, 500);
    }

    event.eventEmitter.on(event.EXPAND_TREE_NODE,(nodeData)=>{
        const $span=$('#node-'+nodeData.id).parent("li");
        var $liChildren =  $span.parent('li.parent_li').find(' > ul > li');
        $liChildren.show('fast');
        $span.children('i:first').addClass('rf-minus').removeClass('rf-plus');
    })
});

