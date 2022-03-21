import BaseClient from "./baseClient";

class KieServerClient extends BaseClient {
  constructor() {
    super("kieservers");
  }

  getKieServers() {
    return this.instance.get().then(res => res.data);
  }

  getDefinitions(kieServerId) {
    return this.instance
      .get(this.buildUrl(kieServerId, "definitions"))
      .then(res => res.data);
  }

  getDefinition(kieServerId, containerId, processId) {
    return this.instance
      .get(this.buildUrl(kieServerId, "definitions", containerId, processId))
      .then(res => res.data);
  }

  getInstances(
    kieServerId,
    containerId,
    page,
    pageSize,
    sortingColumn,
    sortingOrder
  ) {
    return this.instance.get(
      this.buildUrl(kieServerId, "instances", containerId),
      {
        params: {
          page: page - 1,
          pageSize,
          sortBy: sortingColumn,
          orderBy: sortingOrder
        }
      }
    );
  }
}

export default new KieServerClient();
