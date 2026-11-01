import { useState } from 'react'
import { Alert, Button, Card, Input, Space, Statistic, Table, Tag, Typography, message } from 'antd'
import { ScanOutlined } from '@ant-design/icons'
import { senseApi } from '../services/api'

const { TextArea } = Input
const { Paragraph } = Typography

const LEVEL_COLORS = { high: 'red', medium: 'orange', low: 'gold' }

/** 敏感识别试跑：POST /z-team/api/sense/detect。 */
export default function DetectPage() {
    const [text, setText] = useState('')
    const [loading, setLoading] = useState(false)
    const [result, setResult] = useState(null)

    const runDetect = async () => {
        if (!text.trim()) { message.warning('请输入待识别文本'); return }
        setLoading(true)
        try {
            const { data } = await senseApi.detect(text)
            if (!data.ok) throw new Error(data.message || '识别失败')
            setResult(data)
        } catch (e) {
            message.error(`识别失败：${e.response?.status || e.message}`)
        } finally {
            setLoading(false)
        }
    }

    const columns = [
        { title: '规则', dataIndex: 'ruleId', width: 160 },
        { title: '分类', dataIndex: 'category', width: 120, render: (v) => <Tag>{v}</Tag> },
        {
            title: '级别', dataIndex: 'level', width: 90,
            render: (v) => <Tag color={LEVEL_COLORS[v] || 'default'}>{v}</Tag>,
        },
        { title: '位置', key: 'pos', width: 110, render: (_, r) => `[${r.start}, ${r.end})` },
        { title: '命中片段', dataIndex: 'snippet' },
    ]

    return (
        <div>
            <Card title="敏感信息识别" style={{ marginBottom: 16 }}>
                <Space direction="vertical" style={{ width: '100%' }} size={12}>
                    <TextArea
                        rows={6}
                        value={text}
                        onChange={(e) => setText(e.target.value)}
                        placeholder="粘贴待识别文本，例如含手机号 / 身份证 / 银行卡号的段落"
                    />
                    <Button type="primary" icon={<ScanOutlined />} loading={loading} onClick={runDetect}>
                        识别
                    </Button>
                </Space>
            </Card>

            {result && (
                <>
                    <Card style={{ marginBottom: 16 }}>
                        <Space size={48} wrap>
                            <Statistic title="命中数" value={result.hitCount} />
                            <Statistic
                                title="含敏感内容"
                                value={result.sensitive ? '是' : '否'}
                                valueStyle={{ color: result.sensitive ? '#dc2626' : '#059669' }}
                            />
                            <Statistic title="最高级别" value={result.maxLevel || '-'} />
                            <Statistic title="耗时" suffix="ms" value={result.elapsedMillis} />
                        </Space>
                    </Card>
                    <Card title="命中明细">
                        {result.hits?.length ? (
                            <Table
                                rowKey={(_, i) => i}
                                size="small"
                                columns={columns}
                                dataSource={result.hits}
                                pagination={{ pageSize: 10, showSizeChanger: false }}
                            />
                        ) : (
                            <Alert type="success" showIcon message="未命中任何敏感规则。" />
                        )}
                    </Card>
                </>
            )}
            {!result && (
                <Paragraph type="secondary" style={{ textAlign: 'center' }}>
                    输入文本后点「识别」，命中结果在此展示。
                </Paragraph>
            )}
        </div>
    )
}
