import type { DashboardDay } from '../../api/types'
import { formatNumber } from '../../format'
import { useI18n } from '../../i18n/context'

const WIDTH = 720
const HEIGHT = 220
const PAD = { top: 12, right: 44, bottom: 28, left: 44 }
const PLOT_W = WIDTH - PAD.left - PAD.right
const PLOT_H = HEIGHT - PAD.top - PAD.bottom
/** x축 날짜 라벨은 이 개수 안팎으로만 그린다(겹치지 않게). */
const X_LABELS = 7

/** 축 최댓값을 1·2·5 × 10^n으로 올린다. 0이면 1(빈 그래프도 축이 선다). */
function niceMax(value: number): number {
  if (value <= 0) return 1
  const magnitude = 10 ** Math.floor(Math.log10(value))
  for (const m of [1, 2, 5, 10]) {
    if (value <= m * magnitude) return m * magnitude
  }
  return 10 * magnitude
}

/**
 * 일별 추이. 방문은 막대(왼쪽 축), 신청은 선(오른쪽 축)이고 축마다 하나의 눈금을 쓴다. 차트 라이브러리 없이 inline SVG로 그린다.
 * 색은 흑백 토큰(CSS 클래스)만 쓰므로 라이트·다크 테마를 따라간다. 날짜마다 투명한 칸에 title을 달아 마우스를 올리면 값이 보이고,
 * 스크린 리더는 같은 데이터를 담은 숨긴 표를 읽는다(그래프 자체는 role="img"와 이름만 갖는다).
 */
export function TrendChart({ days }: { days: DashboardDay[] }) {
  const { t } = useI18n()
  const maxVisits = niceMax(Math.max(0, ...days.map((d) => d.visits)))
  const maxLeads = niceMax(Math.max(0, ...days.map((d) => d.leads)))
  const slot = days.length > 0 ? PLOT_W / days.length : PLOT_W
  const barW = Math.max(1, slot * 0.6)
  const x = (i: number) => PAD.left + slot * i + slot / 2
  const yVisits = (v: number) => PAD.top + PLOT_H - (v / maxVisits) * PLOT_H
  const yLeads = (v: number) => PAD.top + PLOT_H - (v / maxLeads) * PLOT_H
  const every = Math.max(1, Math.ceil(days.length / X_LABELS))
  const line = days.map((d, i) => `${i === 0 ? 'M' : 'L'}${x(i).toFixed(1)},${yLeads(d.leads).toFixed(1)}`).join(' ')
  const ticks = [0, 0.5, 1]

  return (
    <figure className="trend">
      <ul className="trend-legend" aria-hidden="true">
        <li>
          <span className="trend-key trend-key-bar" />
          {t('dashboard.legendVisits')}
        </li>
        <li>
          <span className="trend-key trend-key-line" />
          {t('dashboard.legendLeads')}
        </li>
      </ul>
      <svg className="trend-chart" viewBox={`0 0 ${WIDTH} ${HEIGHT}`} role="img" aria-label={t('dashboard.trendChart')}>
        {ticks.map((f) => {
          const y = PAD.top + PLOT_H - f * PLOT_H
          return (
            <g key={f}>
              <line className="trend-grid" x1={PAD.left} x2={WIDTH - PAD.right} y1={y} y2={y} />
              <text className="trend-axis" x={PAD.left - 6} y={y} textAnchor="end" dominantBaseline="middle">
                {formatNumber(maxVisits * f)}
              </text>
              <text className="trend-axis" x={WIDTH - PAD.right + 6} y={y} textAnchor="start" dominantBaseline="middle">
                {formatNumber(maxLeads * f)}
              </text>
            </g>
          )
        })}
        {days.map((d, i) => (
          <rect
            key={d.date}
            className="trend-bar"
            x={x(i) - barW / 2}
            y={yVisits(d.visits)}
            width={barW}
            height={PAD.top + PLOT_H - yVisits(d.visits)}
          />
        ))}
        {days.length > 0 && <path className="trend-line" d={line} />}
        {days.length <= 31 &&
          days.map((d, i) => <circle key={d.date} className="trend-dot" cx={x(i)} cy={yLeads(d.leads)} r={2.5} />)}
        {days.map((d, i) =>
          i % every === 0 || i === days.length - 1 ? (
            <text key={d.date} className="trend-axis" x={x(i)} y={HEIGHT - 8} textAnchor="middle">
              {d.date.slice(5)}
            </text>
          ) : null,
        )}
        {days.map((d, i) => (
          <rect key={d.date} className="trend-hit" x={PAD.left + slot * i} y={PAD.top} width={slot} height={PLOT_H}>
            <title>
              {t('dashboard.tooltip', { date: d.date, visits: formatNumber(d.visits), leads: formatNumber(d.leads) })}
            </title>
          </rect>
        ))}
      </svg>
      <div className="visually-hidden">
        <table>
          <caption>{t('dashboard.trendTable')}</caption>
          <thead>
            <tr>
              <th scope="col">{t('dashboard.date')}</th>
              <th scope="col">{t('common.visits')}</th>
              <th scope="col">{t('common.visitors')}</th>
              <th scope="col">{t('common.leads')}</th>
              <th scope="col">{t('common.convertedVisitors')}</th>
            </tr>
          </thead>
          <tbody>
            {days.map((d) => (
              <tr key={d.date}>
                <th scope="row">{d.date}</th>
                <td>{formatNumber(d.visits)}</td>
                <td>{formatNumber(d.visitors)}</td>
                <td>{formatNumber(d.leads)}</td>
                <td>{formatNumber(d.convertedVisitors)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </figure>
  )
}
