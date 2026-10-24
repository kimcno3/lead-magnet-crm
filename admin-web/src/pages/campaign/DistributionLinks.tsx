import { useState } from 'react'
import { Link } from 'react-router'
import { listCreators } from '../../api/creators'
import { createLink } from '../../api/links'
import { LINK_CHANNELS, type DistributionLink, type LinkChannel } from '../../api/types'
import { CreatorStatusBadge } from '../../components/Badge'
import { Button } from '../../components/Button'
import { CopyButton } from '../../components/CopyButton'
import { ErrorMessage } from '../../components/ErrorMessage'
import { TableWrap } from '../../components/Table'
import { errorText } from '../../errors'
import { useAsync } from '../../hooks'
import { useI18n } from '../../i18n/context'
import { channelLabel } from '../../labels'

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

function utmText(link: DistributionLink): string {
  return [link.utmSource, link.utmMedium, link.utmCampaign, link.utmContent].filter(Boolean).join(' / ')
}

/**
 * 폼의 배포 링크를 한 표로 보여 준다(채널 × 대상, ADR 0006·0014). 첫 행은 기본 링크(크리에이터 없음)이고,
 * 링크가 있는 크리에이터와 운영자가 고른 크리에이터가 그 아래 행이다. 링크는 데이터에서도 한 종류이며
 * 크리에이터가 비어 있으면 기본 링크다. 보관된 크리에이터는 고를 수 없고, 이미 링크가 있으면 행은 남지만 새 링크는 만들 수 없다.
 */
export function DistributionLinks({ formId, closed, links, linksError, onReloadLinks }: DistributionLinksProps) {
  const { t, rich } = useI18n()
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
  const rows: Row[] = [{ key: 'default', creatorId: null, name: t('links.defaultRow'), archived: false }, ...creatorRows]
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
      setError(t('links.createFailed', { name: row.name, channel: channelLabel(channel), error: errorText(e) }))
    } finally {
      setBusy(null)
    }
  }

  return (
    <div className="links-block">
      <div className="links-head">
        <h3>{t('forms.distributionLinks')}</h3>
        {creators.error ? (
          <ErrorMessage error={creators.error} onRetry={creators.reload} />
        ) : !creators.data ? (
          <div className="inline-row" aria-busy="true">
            <span className="skeleton" aria-hidden="true" />
            <span className="visually-hidden">{t('links.loadingCreators')}</span>
          </div>
        ) : creators.data.length === 0 ? (
          <p className="muted small">
            {rich('links.registerFirst', { link: <Link key="link" to="/creators">{t('links.creatorMenu')}</Link> })}
          </p>
        ) : (
          <div className="inline-row" role="group" aria-label={t('links.addRowGroup')}>
            <label className="visually-hidden" htmlFor={`creator-pick-${formId}`}>
              {t('common.creator')}
            </label>
            <select
              id={`creator-pick-${formId}`}
              className="inline-select"
              value={selected}
              onChange={(e) => setSelected(e.target.value)}
              disabled={options.length === 0}
            >
              <option value="">{options.length === 0 ? t('links.noCreatorToAdd') : t('links.select')}</option>
              {options.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
            <Button
              size="sm"
              disabled={!selected}
              onClick={() => {
                setAdded((prev) => [...prev, Number(selected)])
                setSelected('')
              }}
            >
              {t('links.addRow')}
            </Button>
          </div>
        )}
      </div>
      <ErrorMessage error={linksError} onRetry={onReloadLinks} />
      {error && (
        <p className="form-error" role="alert">
          {error}
        </p>
      )}
      <TableWrap label={t('forms.distributionLinks')}>
        <table className="table table-compact">
          <caption className="visually-hidden">{t('forms.distributionLinks')}</caption>
          <thead>
            <tr>
              <th scope="col" className="shrink">{t('links.target')}</th>
              {LINK_CHANNELS.map((ch) => (
                <th key={ch} scope="col">
                  {channelLabel(ch)}
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
                    {row.creatorId === null && <span className="muted small">{t('links.noCreator')}</span>}
                    {row.archived && <CreatorStatusBadge status="archived" />}
                  </span>
                </th>
                {LINK_CHANNELS.map((ch) => {
                  const link = linkFor(row, ch)
                  return (
                    <td key={ch}>
                      {link ? (
                        <span className="link-cell" title={`${link.url}\nUTM: ${utmText(link)}`} data-url={link.url}>
                          <CopyButton text={link.url} label={t('links.copy')} />
                        </span>
                      ) : links ? (
                        <Button
                          size="sm"
                          aria-label={t('links.create', { name: row.name, channel: channelLabel(ch) })}
                          onClick={() => make(row, ch)}
                          busy={busy === `${row.key}-${ch}`}
                          disabled={closed || row.archived || busy !== null}
                        >
                          {t('common.create')}
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
        {t('links.note')}
      </p>

    </div>
  )
}
