import BaseClient from "./baseClient";
import DOMPurify from "dompurify";

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
          sortBy: DOMPurify.sanitize(sortingColumn),
          orderBy: DOMPurify.sanitize(sortingOrder)
        }
      }
    );
  }
}

export default new KieServerClient();
