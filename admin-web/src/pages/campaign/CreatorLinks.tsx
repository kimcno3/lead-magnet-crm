import { useState } from 'react'
import { Link } from 'react-router'
import { listCreators } from '../../api/creators'
import { createLink } from '../../api/links'
import { LINK_CHANNELS, type DistributionLink, type LinkChannel } from '../../api/types'
import { CreatorStatusBadge } from '../../components/Badge'
import { Button } from '../../components/Button'
import { CopyButton } from '../../components/CopyButton'
import { ErrorMessage } from '../../components/ErrorMessage'
import { Field } from '../../components/Field'
import { TableWrap } from '../../components/Table'
import { errorText } from '../../errors'
import { useAsync } from '../../hooks'
import { CHANNEL_LABELS } from '../../labels'

interface CreatorLinksProps {
  formId: number
  /** 마감된 폼이면 새 링크를 만들 수 없다 */
  closed: boolean
  links: DistributionLink[] | undefined
  onCreated: () => void
}

/**
 * 폼의 크리에이터별 배포 링크(채널 × 크리에이터 표, ADR 0014). 링크가 있는 크리에이터와 운영자가 고른 크리에이터가 행이다.
 * 보관된 크리에이터는 고를 수 없고, 이미 링크가 있으면 행은 남지만 새 링크는 만들 수 없다.
 */
export function CreatorLinks({ formId, closed, links, onCreated }: CreatorLinksProps) {
  const creators = useAsync(listCreators, [])
  const [added, setAdded] = useState<number[]>([])
  const [selected, setSelected] = useState('')
  const [busy, setBusy] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const creatorLinks = links?.filter((l) => l.creator) ?? []
  const linkedIds = new Set(creatorLinks.map((l) => l.creator!.id))
  const rows = (creators.data ?? [])
    .filter((c) => linkedIds.has(c.id) || added.includes(c.id))
    .sort((a, b) => a.id - b.id)
  const options = (creators.data ?? []).filter((c) => c.status === 'active' && !rows.some((r) => r.id === c.id))

  async function make(creatorId: number, creatorName: string, channel: LinkChannel) {
    setBusy(`${creatorId}-${channel}`)
    setError(null)
    try {
      await createLink(formId, channel, creatorId)
      onCreated()
    } catch (e) {
      setError(`${creatorName} ${CHANNEL_LABELS[channel]} 링크를 만들지 못했습니다: ${errorText(e)}`)
    } finally {
      setBusy(null)
    }
  }

  if (creators.error) return <ErrorMessage error={creators.error} onRetry={creators.reload} />
  if (!creators.data) return null
  if (creators.data.length === 0) {
    return (
      <p className="muted small">
        수익 배분 크리에이터별로 링크를 나누려면 <Link to="/creators">크리에이터 메뉴</Link>에서 먼저 등록하세요.
      </p>
    )
  }

  return (
    <>
      <div className="toolbar" role="group" aria-label="크리에이터 추가">
        <Field label="크리에이터">
          {(p) => (
            <select {...p} value={selected} onChange={(e) => setSelected(e.target.value)} disabled={options.length === 0}>
              <option value="">{options.length === 0 ? '추가할 크리에이터 없음' : '선택'}</option>
              {options.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          )}
        </Field>
        <Button
          size="sm"
          disabled={!selected}
          onClick={() => {
            setAdded((prev) => [...prev, Number(selected)])
            setSelected('')
          }}
        >
          표에 추가
        </Button>
      </div>
      {error && (
        <p className="form-error" role="alert">
          {error}
        </p>
      )}
      {rows.length > 0 && (
        <TableWrap label="크리에이터별 배포 링크">
          <table className="table table-compact">
            <caption className="visually-hidden">크리에이터별 배포 링크</caption>
            <thead>
              <tr>
                <th scope="col">크리에이터</th>
                {LINK_CHANNELS.map((ch) => (
                  <th key={ch} scope="col">
                    {CHANNEL_LABELS[ch]}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {rows.map((c) => (
                <tr key={c.id}>
                  <th scope="row" className="nowrap">
                    <span className="inline-row">
                      {c.name}
                      {c.status === 'archived' && <CreatorStatusBadge status="archived" />}
                    </span>
                  </th>
                  {LINK_CHANNELS.map((ch) => {
                    const link = creatorLinks.find((l) => l.creator!.id === c.id && l.channel === ch)
                    return (
                      <td key={ch}>
                        {link ? (
                          <span className="inline-row">
                            <code className="url" translate="no" title={link.url}>
                              {link.code}
                            </code>
                            <CopyButton text={link.url} />
                          </span>
                        ) : (
                          <Button
                            size="sm"
                            aria-label={`${c.name} ${CHANNEL_LABELS[ch]} 링크 만들기`}
                            onClick={() => make(c.id, c.name, ch)}
                            busy={busy === `${c.id}-${ch}`}
                            disabled={closed || c.status === 'archived' || busy !== null}
                          >
                            만들기
                          </Button>
                        )}
                      </td>
                    )
                  })}
                </tr>
              ))}
            </tbody>
          </table>
        </TableWrap>
      )}
    </>
  )
}
