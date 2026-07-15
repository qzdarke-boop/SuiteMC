package com.psdk.thepit.topboard;

/**
 * Estado de visualização individual de um jogador em um top board (período + página).
 */
public final class PlayerBoardView {

    private TopPeriod period = TopPeriod.WEEKLY;
    private int page;

    public TopPeriod getPeriod() { return period; }

    public void setPeriod(TopPeriod period) {
        this.period = period != null ? period : TopPeriod.WEEKLY;
    }

    public TopPeriod cyclePeriod() {
        period = period.next();
        return period;
    }

    public int getPage() { return page; }

    public void setPage(int page) {
        this.page = Math.max(0, Math.min(9, page));
    }

    public int nextPage() {
        page = (page + 1) % 10;
        return page;
    }

    public int rankStart() {
        return page * TopQueryService.PAGE_SIZE + 1;
    }

    public int rankEnd() {
        return rankStart() + TopQueryService.PAGE_SIZE - 1;
    }
}
