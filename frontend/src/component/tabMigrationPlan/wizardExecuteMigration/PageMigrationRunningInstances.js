import classNames from "classnames";
import { orderBy } from "lodash";
import {
  customHeaderFormattersDefinition,
  defaultSortingOrder,
  Grid,
  PAGINATION_VIEW,
  PaginationRow,
  sortableHeaderCellFormatter,
  Table,
  TABLE_SORT_DIRECTION,
  tableCellFormatter
} from "patternfly-react";
import PropTypes from "prop-types";
import React from "react";
import { compose } from "recompose";
import * as sort from "sortabular";
import * as resolve from "table-resolver";

export default class PageMigrationRunningInstances extends React.Component {
  constructor(props) {
    super(props);

    const getSortingColumns = () => this.state.sortingColumns || {};

    const sortableTransform = sort.sort({
      getSortingColumns,
      onSort: selectedColumn => {
        let sortingColumns = sort.byColumn({
          sortingColumns: this.state.sortingColumns,
          sortingOrder: defaultSortingOrder,
          selectedColumn
        });
        this.setState({
          sortingColumns
        });
        this.props
          .getRunningInstances(
            this.state.pagination.page,
            this.state.pagination.perPage,
            sortingColumns
          )
          .then(res => {
            this.setState({
              rows: res.data,
              totalInstances: parseInt(res.headers["x-total-count"])
            });
          });
      },
      // Use property or index dependening on the sortingColumns structure specified
      strategy: sort.strategies.byProperty
    });

    const sortingFormatter = sort.header({
      sortableTransform,
      getSortingColumns,
      strategy: sort.strategies.byProperty
    });

    const stateFormatter = value => {
      const formatState = state => {
        switch (state) {
          case 0:
            return "Pending";
          case 1:
            return "Active";
          case 2:
            return "Completed";
          case 3:
            return "Aborted";
          case 4:
            return "Suspended";
          default:
            return "Other";
        }
      };
      return <Table.Cell>{formatState(value)}</Table.Cell>;
    };

    // enables our custom header formatters extensions to reactabular
    this.customHeaderFormatters = customHeaderFormattersDefinition;
    this.state = {
      // Sort the first column in an ascending way by default.
      sortingColumns: {
        processInstanceId: {
          direction: TABLE_SORT_DIRECTION.ASC,
          position: 0
        }
      },

      // column definitions
      columns: [
        {
          property: "processInstanceId",
          header: {
            label: "ID",
            props: {
              index: 0,
              rowSpan: 1,
              colSpan: 1
            },
            transforms: [sortableTransform],
            formatters: [sortingFormatter],
            customFormatters: [sortableHeaderCellFormatter]
          },
          cell: {
            props: {
              index: 1
            },
            formatters: [tableCellFormatter]
          }
        },
        {
          property: "name",
          header: {
            label: "Name",
            props: {
              index: 1,
              rowSpan: 1,
              colSpan: 1
            },
            transforms: [sortableTransform],
            formatters: [sortingFormatter],
            customFormatters: [sortableHeaderCellFormatter]
          },
          cell: {
            props: {
              index: 1
            },
            formatters: [tableCellFormatter]
          }
        },
        {
          property: "description",
          header: {
            label: "Description",
            props: {
              index: 2,
              rowSpan: 1,
              colSpan: 1
            },
            transforms: [sortableTransform],
            formatters: [sortingFormatter],
            customFormatters: [sortableHeaderCellFormatter]
          },
          cell: {
            props: {
              index: 2
            },
            formatters: [tableCellFormatter]
          }
        },
        {
          property: "startTime",
          header: {
            label: "Start Time",
            props: {
              index: 3,
              rowSpan: 1,
              colSpan: 1
            },
            transforms: [sortableTransform],
            formatters: [sortingFormatter],
            customFormatters: [sortableHeaderCellFormatter]
          },
          cell: {
            props: {
              index: 3
            },
            formatters: [tableCellFormatter]
          }
        },
        {
          property: "state",
          header: {
            label: "State",
            props: {
              index: 4,
              rowSpan: 1,
              colSpan: 1
            },
            transforms: [sortableTransform],
            formatters: [sortingFormatter],
            customFormatters: [sortableHeaderCellFormatter]
          },
          cell: {
            props: {
              index: 4
            },
            formatters: [stateFormatter]
          }
        }
      ],

      // rows and row selection state
      rows: [],
      selectedRows: [],

      // pagination default states
      pagination: {
        page: 1,
        perPage: 6,
        perPageOptions: [6, 10, 15]
      },

      // page input value
      pageChangeValue: 1
    };
  }

  deselectRow = row => {
    return Object.assign({}, row, { selected: false });
  };

  selectRow = row => {
    return Object.assign({}, row, { selected: true });
  };

  onFirstPage = () => {
    this.setPage(1);
  };
  onLastPage = () => {
    const { page } = this.state.pagination;
    const totalPages = this.totalPages();
    if (page < totalPages) {
      this.setPage(totalPages);
    }
  };
  onNextPage = () => {
    const { page } = this.state.pagination;
    if (page < this.totalPages()) {
      this.setPage(this.state.pagination.page + 1);
    }
  };
  onPageInput = e => {
    this.setState({ pageChangeValue: e.target.value });
  };
  onPerPageSelect = eventKey => {
    const newPaginationState = Object.assign({}, this.state.pagination);
    newPaginationState.perPage = eventKey;
    newPaginationState.page = 1;
    this.setState({ pagination: newPaginationState });
    this.props
      .getRunningInstances(
        newPaginationState.page,
        newPaginationState.perPage,
        this.state.sortingColumns
      )
      .then(res => {
        this.setState({
          rows: res.data,
          totalInstances: parseInt(res.headers["x-total-count"])
        });
      });
  };
  onPreviousPage = () => {
    if (this.state.pagination.page > 1) {
      this.setPage(this.state.pagination.page - 1);
    }
  };
  onRow = row => {
    const { selectedRows } = this.state;
    const selected = selectedRows.includes(row.processInstanceId);
    return {
      onClick: () => this.onSelectRow(row),
      className: classNames({ selected }),
      role: "row"
    };
  };

  updateSelectedProcessIds = (rows, selectedRows) => {
    this.props.setRunningInstancesIds(selectedRows);
    this.props.onIsValid(selectedRows.length > 0);
  };

  componentDidUpdate() {
    if (this.props.migrateAll && this.state.selectedRows.length > 0) {
      this.clearSelection();
    }
  }

  clearSelection = () => {
    const { rows, selectedRows } = this.state;
    const updatedRows = rows.map(r =>
      selectedRows.includes(r.processInstanceId) ? r : this.deselectRow(r)
    );
    this.setState({
      rows: updatedRows,
      selectedRows: []
    });
    this.updateSelectedProcessIds(rows, []);
  };

  onSelectRow = row => {
    const { rows, selectedRows } = this.state;
    const selectedRowIndex = rows.findIndex(r => r.id === row.id);
    if (selectedRowIndex > -1) {
      let updatedSelectedRows;
      let updatedRow;
      if (row.selected) {
        updatedSelectedRows = selectedRows.filter(
          r => !(r === row.processInstanceId)
        );
        updatedRow = this.deselectRow(row);
      } else {
        selectedRows.push(row.processInstanceId);
        updatedSelectedRows = selectedRows;
        updatedRow = this.selectRow(row);
      }
      rows[selectedRowIndex] = updatedRow;
      this.setState({
        rows,
        selectedRows: updatedSelectedRows
      });
      this.updateSelectedProcessIds(rows, updatedSelectedRows);
    }
  };

  onSubmit = () => {
    this.setPage(this.state.pageChangeValue);
  };
  setPage = page => {
    if (
      !this.state.totalInstances ||
      (!isNaN(page) && page !== "" && page > 0 && page <= this.totalPages())
    ) {
      this.props
        .getRunningInstances(
          page,
          this.state.pagination.perPage,
          this.state.sortingColumns
        )
        .then(res => {
          this.setState({
            rows: res.data,
            totalInstances: parseInt(res.headers["x-total-count"])
          });
          const newPaginationState = Object.assign({}, this.state.pagination);
          newPaginationState.page = page;
          this.setState({
            pagination: newPaginationState,
            pageChangeValue: page
          });
        })
        .catch(() => {
          this.setState({
            rows: [],
            totalInstances: 0
          });
        });
    }
  };

  currentRows() {
    const { rows, sortingColumns, columns, pagination } = this.state;
    return compose(
      this.paginate(pagination.page, pagination.perPage),
      sort.sorter({
        columns,
        sortingColumns,
        sort: orderBy,
        strategy: sort.strategies.byProperty
      })
    )(rows);
  }

  totalPages = () => {
    const { perPage } = this.state.pagination;
    return Math.ceil(this.state.totalInstances / perPage);
  };

  async componentDidMount() {
    const res = await this.props.getRunningInstances(
      1,
      this.state.pagination.perPage,
      this.state.sortingColumns
    );
    this.setState({
      rows: res.data,
      totalInstances: parseInt(res.headers["x-total-count"])
    });
  }

  paginate(page, perPage) {
    const totalInstances = this.state.totalInstances;
    const currentRows = this.state.rows;

    return function() {
      // adapt to zero indexed logic
      var p = page - 1 || 0;
      var amountOfPages = Math.ceil(totalInstances / perPage);
      var startPage = p < amountOfPages ? p : 0;
      var endOfPage = startPage * perPage + perPage;
      return {
        amountOfPages: amountOfPages,
        itemCount: totalInstances,
        itemsStart: startPage * perPage + 1,
        itemsEnd: endOfPage > totalInstances ? totalInstances : endOfPage,
        rows: currentRows
      };
    };
  }

  render() {
    const { columns, pagination, sortingColumns, pageChangeValue } = this.state;

    const sortedPaginatedRows = this.currentRows();

    return (
      <Grid fluid>
        <Table.PfProvider
          striped
          bordered
          hover
          dataTable
          columns={columns}
          components={{
            header: {
              cell: cellProps =>
                this.customHeaderFormatters({
                  cellProps,
                  columns,
                  sortingColumns,
                  rows: sortedPaginatedRows.rows
                })
            }
          }}
        >
          <Table.Header headerRows={resolve.headerRows({ columns })} />
          <Table.Body
            rows={sortedPaginatedRows.rows}
            rowKey="processInstanceId"
            onRow={this.onRow}
          />
        </Table.PfProvider>
        <PaginationRow
          viewType={PAGINATION_VIEW.TABLE}
          pagination={pagination}
          pageInputValue={pageChangeValue}
          amountOfPages={sortedPaginatedRows.amountOfPages}
          itemCount={sortedPaginatedRows.itemCount}
          itemsStart={sortedPaginatedRows.itemsStart}
          itemsEnd={sortedPaginatedRows.itemsEnd}
          onPerPageSelect={this.onPerPageSelect}
          onFirstPage={this.onFirstPage}
          onPreviousPage={this.onPreviousPage}
          onPageInput={this.onPageInput}
          onNextPage={this.onNextPage}
          onLastPage={this.onLastPage}
          onSubmit={this.onSubmit}
        />
      </Grid>
    );
  }
}

PageMigrationRunningInstances.propTypes = {
  getRunningInstances: PropTypes.func.isRequired,
  setRunningInstancesIds: PropTypes.func.isRequired,
  onIsValid: PropTypes.func.isRequired,
  migrateAll: PropTypes.bool.isRequired
};
