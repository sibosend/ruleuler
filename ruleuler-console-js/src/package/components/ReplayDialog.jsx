import React, { Component } from 'react';
import ReactDOM from 'react-dom';
import CommonDialog from '../../components/dialog/component/CommonDialog.jsx';
import * as event from '../event.js';

const TIME_OPTIONS = [
    { value: 1, label: '过去1天' },
    { value: 3, label: '过去3天' },
    { value: 7, label: '过去1周' },
    { value: 30, label: '过去1月' },
];

const SAMPLE_OPTIONS = [
    { value: 'all', label: '全量' },
    { value: 'random', label: '随机采样' },
    { value: 'uniform', label: '均匀采样' },
];

const MISSING_OPTIONS = [
    { value: 'segment', label: '区间填充（缺失key自动填充值域代表值）' },
    { value: 'null', label: '不填充（缺失变量不填值）' },
    { value: 'skip', label: '跳过（缺失变量的记录不执行）' },
];

export default class ReplayDialog extends Component {
    constructor(props) {
        super(props);
        this.state = {
            pkgId: '', pkgName: '', project: '',
            timeDays: 7, sampleStrategy: 'all', sampleSize: 10000,
            missingStrategy: 'segment', submitting: false,
        };
    }

    componentDidMount() {
        event.eventEmitter.on(event.OPEN_REPLAY_DIALOG, (config) => {
            this.setState({
                pkgId: config.pkgId, pkgName: config.pkgName, project: config.project,
                timeDays: 7, sampleStrategy: 'all', sampleSize: 10000,
                missingStrategy: 'segment', submitting: false,
            });
            $(ReactDOM.findDOMNode(this)).modal({ show: true, backdrop: 'static', keyboard: false });
        });
        event.eventEmitter.on(event.HIDE_REPLAY_DIALOG, () => {
            $(ReactDOM.findDOMNode(this)).modal('hide');
        });
    }

    handleCreate() {
        var pkgId = this.state.pkgId;
        var project = this.state.project;
        var timeDays = this.state.timeDays;
        var sampleStrategy = this.state.sampleStrategy;
        var sampleSize = this.state.sampleSize;
        var missingStrategy = this.state.missingStrategy;
        var now = Date.now();
        var startTime = now - timeDays * 24 * 3600 * 1000;
        this.setState({ submitting: true });

        fetch('/api/replay/tasks', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include',
            body: JSON.stringify({
                project, packageId: pkgId,
                startTime, endTime: now,
                sampleStrategy, sampleSize,
                missingVarStrategy: missingStrategy,
            }),
        }).then(res => {
            if (res.ok) return res.json();
            return res.json().then(d => { throw new Error(d.message || res.status); });
        }).then(result => {
            this.setState({ submitting: false });
            event.eventEmitter.emit(event.HIDE_REPLAY_DIALOG);
            var taskId = (result && result.data && result.data.taskId) ? result.data.taskId : null;
            var msg = taskId ? '回放任务 #' + taskId + ' 已创建' : '回放任务已创建';
            bootbox.confirm({
                title: msg,
                message: '是否跳转到回放页面查看？',
                buttons: { confirm: { label: '去查看', className: 'btn-primary' }, cancel: { label: '留在此页', className: 'btn-default' } },
                callback: (confirmed) => {
                    if (confirmed) {
                        window.top.location.href = '/admin/projects/' + encodeURIComponent(project) + '/replay?packageId=' + encodeURIComponent(pkgId);
                    }
                },
            });
        }).catch(e => {
            this.setState({ submitting: false });
            bootbox.alert('创建失败：' + e.message);
        });
    }

    render() {
        var pkgName = this.state.pkgName;
        var timeDays = this.state.timeDays;
        var sampleStrategy = this.state.sampleStrategy;
        var sampleSize = this.state.sampleSize;
        var missingStrategy = this.state.missingStrategy;
        var submitting = this.state.submitting;
        const body = (
            <div className="form-horizontal" style={{ padding: '10px' }}>
                <div className="form-group">
                    <label className="col-xs-4 control-label">知识包</label>
                    <div className="col-xs-8">
                        <input type="text" className="form-control" disabled value={pkgName} />
                    </div>
                </div>
                <div className="form-group">
                    <label className="col-xs-4 control-label">时间范围</label>
                    <div className="col-xs-8">
                        <select className="form-control" value={timeDays}
                            onChange={(e) => this.setState({ timeDays: parseInt(e.target.value) })}>
                            {TIME_OPTIONS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
                        </select>
                    </div>
                </div>
                <div className="form-group">
                    <label className="col-xs-4 control-label">采样方式</label>
                    <div className="col-xs-8">
                        <select className="form-control" value={sampleStrategy}
                            onChange={(e) => this.setState({ sampleStrategy: e.target.value })}>
                            {SAMPLE_OPTIONS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
                        </select>
                    </div>
                </div>
                <div className="form-group">
                    <label className="col-xs-4 control-label">采样数量</label>
                    <div className="col-xs-8">
                        <input type="number" className="form-control" min={1} max={10000}
                            value={sampleSize} onChange={(e) => this.setState({ sampleSize: parseInt(e.target.value) || 10000 })} />
                    </div>
                </div>
                <div className="form-group">
                    <label className="col-xs-4 control-label">缺失key填充策略</label>
                    <div className="col-xs-8">
                        <select className="form-control" value={missingStrategy}
                            onChange={(e) => this.setState({ missingStrategy: e.target.value })}>
                            {MISSING_OPTIONS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
                        </select>
                    </div>
                </div>
            </div>
        );
        const buttons = [{
            name: submitting ? '创建中...' : '创建回放任务',
            className: 'btn btn-primary',
            icon: 'glyphicon glyphicon-play',
            click: () => { if (!submitting) this.handleCreate(); },
        }];
        return <CommonDialog large={false} title="流量回放" body={body} buttons={buttons} />;
    }
}
