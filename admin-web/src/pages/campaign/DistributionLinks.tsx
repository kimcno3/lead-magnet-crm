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

interface DistributionLinksProps {
  formId: number
  /** 마감된 폼이면 새 링크를 만들 수 없다 */
  closed: boolean
  links: DistributionLink[] | undefined
  linksError: unknown
  onReloadLinks: () => void
}

/** 표의 한 행: 기본(크리에이터 없음) 또는 크리에이터 한 명 */
interface Row {
  key: string
  creatorId: number | null
  name: string
  archived: boolean
}

const DEFAULT_ROW_NAME = '기본'

function utmText(link: DistributionLink): string {
  return [link.utmSource, link.utmMedium, link.utmCampaign, link.utmContent].filter(Boolean).join(' / ')
}

/**
 * 폼의 배포 링크를 한 표로 보여 준다(채널 × 대상, ADR 0006·0014). 첫 행은 기본 링크(크리에이터 없음)이고,
 * 링크가 있는 크리에이터와 운영자가 고른 크리에이터가 그 아래 행이다. 링크는 데이터에서도 한 종류이며
 * 크리에이터가 비어 있으면 기본 링크다. 보관된 크리에이터는 고를 수 없고, 이미 링크가 있으면 행은 남지만 새 링크는 만들 수 없다.
 */
export function DistributionLinks({ formId, closed, links, linksError, onReloadLinks }: DistributionLinksProps) {
  const creators = useAsync(listCreators, [])
  const [added, setAdded] = useState<number[]>([])
  const [selected, setSelected] = useState('')
  const [busy, setBusy] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const linkedIds = new Set((links ?? []).filter((l) => l.creator).map((l) => l.creator!.id))
  const creatorRows: Row[] = (creators.data ?? [])
    .filter((c) => linkedIds.has(c.id) || added.includes(c.id))
    .sort((a, b) => a.id - b.id)
    .map((c) => ({ key: `c${c.id}`, creatorId: c.id, name: c.name, archived: c.status === 'archived' }))
  const rows: Row[] = [{ key: 'default', creatorId: null, name: DEFAULT_ROW_NAME, archived: false }, ...creatorRows]
  const options = (creators.data ?? []).filter(
    (c) => c.status === 'active' && !creatorRows.some((r) => r.creatorId === c.id),
  )

  function linkFor(row: Row, channel: LinkChannel) {
    return links?.find((l) => l.channel === channel && (l.creator?.id ?? null) === row.creatorId)
  }

  async function make(row: Row, channel: LinkChannel) {
    setBusy(`${row.key}-${channel}`)
    setError(null)
    try {
      await createLink(formId, channel, row.creatorId ?? undefined)
      onReloadLinks()
    } catch (e) {
      setError(`${row.name} ${CHANNEL_LABELS[channel]} 링크를 만들지 못했습니다: ${errorText(e)}`)
    } finally {
      setBusy(null)
    }
  }

  return (
    <div className="links-block">
      <ErrorMessage error={linksError} onRetry={onReloadLinks} />
      {error && (
        <p className="form-error" role="alert">
          {error}
        </p>
      )}
      <TableWrap label="배포 링크">
        <table className="table table-compact">
          <caption className="visually-hidden">배포 링크</caption>
          <thead>
            <tr>
              <th scope="col" className="shrink">대상</th>
              {LINK_CHANNELS.map((ch) => (
                <th key={ch} scope="col">
                  {CHANNEL_LABELS[ch]}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.key}>
                <th scope="row" className="shrink">
                  <span className="inline-row">
                    {row.name}
                    {row.creatorId === null && <span className="muted small">크리에이터 없음</span>}
                    {row.archived && <CreatorStatusBadge status="archived" />}
                  </span>
                </th>
                {LINK_CHANNELS.map((ch) => {
                  const link = linkFor(row, ch)
                  return (
                    <td key={ch}>
                      {link ? (
                        <span className="inline-row">
                          <code className="url" translate="no" title={`${link.url}\nUTM: ${utmText(link)}`} data-url={link.url}>
                            {link.code}
                          </code>
                          <CopyButton text={link.url} />
                        </span>
                      ) : links ? (
                        <Button
                          size="sm"
                          aria-label={`${row.name} ${CHANNEL_LABELS[ch]} 링크 만들기`}
                          onClick={() => make(row, ch)}
                          busy={busy === `${row.key}-${ch}`}
                          disabled={closed || row.archived || busy !== null}
                        >
                          만들기
                        </Button>
                      ) : (
                        <span className="skeleton" aria-hidden="true" />
                      )}
                    </td>
                  )
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </TableWrap>
      <p className="muted small">
        칸에는 짧은 주소(/l/코드)의 코드가 보입니다. 복사 버튼은 전체 주소를 복사하고, 코드에 마우스를 올리면 주소와 UTM 값이 보입니다.
      </p>

      {creators.error ? (
        <ErrorMessage error={creators.error} onRetry={creators.reload} />
      ) : !creators.data ? (
        <div className="toolbar" aria-busy="true">
          <span className="skeleton" aria-hidden="true" />
          <span className="visually-hidden">크리에이터 목록을 불러오는 중</span>
        </div>
      ) : creators.data.length === 0 ? (
        <p className="muted small">
          수익 배분 크리에이터별로 링크를 나누려면 <Link to="/creators">크리에이터 메뉴</Link>에서 먼저 등록하세요.
        </p>
      ) : (
        <div className="toolbar" role="group" aria-label="크리에이터 행 추가">
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
            행 추가
          </Button>
        </div>
      )}
    </div>
  )
}
