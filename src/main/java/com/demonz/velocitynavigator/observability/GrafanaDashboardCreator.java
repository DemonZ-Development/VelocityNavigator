/*
 * Copyright 2026 DemonZ Development
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.demonz.velocitynavigator.observability;
import com.demonz.velocitynavigator.VelocityNavigator;

public final class GrafanaDashboardCreator {

    private GrafanaDashboardCreator() {
    }

    public static String getDashboardJson() {
        return """
        {
          "annotations": {
            "list": []
          },
          "editable": true,
          "fiscalYearStartMonth": 0,
          "graphTooltip": 1,
          "id": null,
          "links": [],
          "liveNow": false,
          "panels": [
            {
              "collapsed": false,
              "gridPos": {
                "h": 1,
                "w": 24,
                "x": 0,
                "y": 0
              },
              "id": 1,
              "title": "Global Metrics",
              "type": "row"
            },
            {
              "datasource": {
                "type": "prometheus",
                "uid": "prometheus"
              },
              "fieldConfig": {
                "defaults": {
                  "color": {
                    "mode": "thresholds"
                  },
                  "mappings": [],
                  "thresholds": {
                    "mode": "absolute",
                    "steps": [
                      {
                        "color": "green",
                        "value": null
                      }
                    ]
                  }
                },
                "overrides": []
              },
              "gridPos": {
                "h": 4,
                "w": 8,
                "x": 0,
                "y": 1
              },
              "id": 2,
              "options": {
                "colorMode": "value",
                "graphMode": "area",
                "justifyMode": "auto",
                "orientation": "auto",
                "reduceOptions": {
                  "calcs": [
                    "lastNotNull"
                  ],
                  "fields": "",
                  "values": false
                },
                "textMode": "auto"
              },
              "targets": [
                {
                  "datasource": {
                    "type": "prometheus",
                    "uid": "prometheus"
                  },
                  "editorMode": "code",
                  "expr": "sum(velocitynavigator_server_players{server=~\\"^$server$\\"})",
                  "legendFormat": "Total Players",
                  "range": true,
                  "refId": "A"
                }
              ],
              "title": "Total Lobby Players",
              "type": "stat"
            },
            {
              "datasource": {
                "type": "prometheus",
                "uid": "prometheus"
              },
              "fieldConfig": {
                "defaults": {
                  "color": {
                    "mode": "thresholds"
                  },
                  "mappings": [],
                  "thresholds": {
                    "mode": "absolute",
                    "steps": [
                      {
                        "color": "red",
                        "value": null
                      },
                      {
                        "color": "green",
                        "value": 1
                      }
                    ]
                  }
                },
                "overrides": []
              },
              "gridPos": {
                "h": 4,
                "w": 8,
                "x": 8,
                "y": 1
              },
              "id": 3,
              "options": {
                "colorMode": "value",
                "graphMode": "none",
                "justifyMode": "auto",
                "orientation": "auto",
                "reduceOptions": {
                  "calcs": [
                    "lastNotNull"
                  ],
                  "fields": "",
                  "values": false
                },
                "textMode": "auto"
              },
              "targets": [
                {
                  "datasource": {
                    "type": "prometheus",
                    "uid": "prometheus"
                  },
                  "editorMode": "code",
                  "expr": "sum(velocitynavigator_server_online{server=~\\"^$server$\\"})",
                  "legendFormat": "Online Lobbies",
                  "range": true,
                  "refId": "A"
                }
              ],
              "title": "Online Lobbies Count",
              "type": "stat"
            },
            {
              "datasource": {
                "type": "prometheus",
                "uid": "prometheus"
              },
              "fieldConfig": {
                "defaults": {
                  "color": {
                    "mode": "thresholds"
                  },
                  "mappings": [],
                  "thresholds": {
                    "mode": "absolute",
                    "steps": [
                      {
                        "color": "green",
                        "value": null
                      },
                      {
                        "color": "yellow",
                        "value": 1
                      }
                    ]
                  }
                },
                "overrides": []
              },
              "gridPos": {
                "h": 4,
                "w": 8,
                "x": 16,
                "y": 1
              },
              "id": 4,
              "options": {
                "colorMode": "value",
                "graphMode": "none",
                "justifyMode": "auto",
                "orientation": "auto",
                "reduceOptions": {
                  "calcs": [
                    "lastNotNull"
                  ],
                  "fields": "",
                  "values": false
                },
                "textMode": "auto"
              },
              "targets": [
                {
                  "datasource": {
                    "type": "prometheus",
                    "uid": "prometheus"
                  },
                  "editorMode": "code",
                  "expr": "sum(velocitynavigator_server_drained{server=~\\"^$server$\\"})",
                  "legendFormat": "Drained Lobbies",
                  "range": true,
                  "refId": "A"
                }
              ],
              "title": "Drained Lobbies",
              "type": "stat"
            },
            {
              "collapsed": false,
              "gridPos": {
                "h": 1,
                "w": 24,
                "x": 0,
                "y": 5
              },
              "id": 5,
              "title": "Server Telemetry & Performance",
              "type": "row"
            },
            {
              "datasource": {
                "type": "prometheus",
                "uid": "prometheus"
              },
              "fieldConfig": {
                "defaults": {
                  "custom": {
                    "drawStyle": "line",
                    "lineInterpolation": "smooth"
                  },
                  "unit": "ms"
                },
                "overrides": []
              },
              "gridPos": {
                "h": 8,
                "w": 12,
                "x": 0,
                "y": 6
              },
              "id": 6,
              "options": {
                "legend": {
                  "calcs": [
                    "mean",
                    "max"
                  ],
                  "displayMode": "table",
                  "placement": "bottom",
                  "showLegend": true
                },
                "tooltip": {
                  "mode": "multi",
                  "sort": "none"
                }
              },
              "targets": [
                {
                  "datasource": {
                    "type": "prometheus",
                    "uid": "prometheus"
                  },
                  "editorMode": "code",
                  "expr": "velocitynavigator_server_latency_ms{server=~\\"^$server$\\"} >= 0",
                  "legendFormat": "{{server}}",
                  "range": true,
                  "refId": "A"
                }
              ],
              "title": "Health Check Latency",
              "type": "timeseries"
            },
            {
              "datasource": {
                "type": "prometheus",
                "uid": "prometheus"
              },
              "fieldConfig": {
                "defaults": {
                  "custom": {
                    "drawStyle": "line",
                    "lineInterpolation": "smooth"
                  }
                },
                "overrides": []
              },
              "gridPos": {
                "h": 8,
                "w": 12,
                "x": 12,
                "y": 6
              },
              "id": 7,
              "options": {
                "legend": {
                  "calcs": [
                    "lastNotNull",
                    "max"
                  ],
                  "displayMode": "table",
                  "placement": "bottom",
                  "showLegend": true
                },
                "tooltip": {
                  "mode": "multi",
                  "sort": "none"
                }
              },
              "targets": [
                {
                  "datasource": {
                    "type": "prometheus",
                    "uid": "prometheus"
                  },
                  "editorMode": "code",
                  "expr": "velocitynavigator_server_players{server=~\\"^$server$\\"}",
                  "legendFormat": "{{server}}",
                  "range": true,
                  "refId": "A"
                }
              ],
              "title": "Player Count Distribution",
              "type": "timeseries"
            },
            {
              "datasource": {
                "type": "prometheus",
                "uid": "prometheus"
              },
              "fieldConfig": {
                "defaults": {
                  "color": {
                    "mode": "thresholds"
                  },
                  "custom": {
                    "fillOpacity": 70,
                    "lineWidth": 1
                  },
                  "mappings": [
                    {
                      "options": {
                        "from": 0,
                        "result": {
                          "color": "green",
                          "text": "CLOSED (Healthy)"
                        },
                        "to": 0
                      },
                      "type": "range"
                    },
                    {
                      "options": {
                        "from": 1,
                        "result": {
                          "color": "yellow",
                          "text": "HALF_OPEN (Testing)"
                        },
                        "to": 1
                      },
                      "type": "range"
                    },
                    {
                      "options": {
                        "from": 2,
                        "result": {
                          "color": "red",
                          "text": "OPEN (Tripped)"
                        },
                        "to": 2
                      },
                      "type": "range"
                    }
                  ],
                  "thresholds": {
                    "mode": "absolute",
                    "steps": [
                      {
                        "color": "green",
                        "value": null
                      },
                      {
                        "color": "yellow",
                        "value": 1
                      },
                      {
                        "color": "red",
                        "value": 2
                      }
                    ]
                  }
                },
                "overrides": []
              },
              "gridPos": {
                "h": 6,
                "w": 24,
                "x": 0,
                "y": 14
              },
              "id": 8,
              "options": {
                "rowHeight": 0.9,
                "showValue": "always",
                "tooltip": {
                  "mode": "single",
                  "sort": "none"
                }
              },
              "targets": [
                {
                  "datasource": {
                    "type": "prometheus",
                    "uid": "prometheus"
                  },
                  "editorMode": "code",
                  "expr": "velocitynavigator_server_circuit_breaker{server=~\\"^$server$\\"}",
                  "legendFormat": "{{server}}",
                  "range": true,
                  "refId": "A"
                }
              ],
              "title": "Circuit Breaker State history",
              "type": "state-timeline"
            },
            {
              "datasource": {
                "type": "prometheus",
                "uid": "prometheus"
              },
              "fieldConfig": {
                "defaults": {
                  "custom": {
                    "drawStyle": "line",
                    "lineInterpolation": "linear"
                  }
                },
                "overrides": []
              },
              "gridPos": {
                "h": 6,
                "w": 24,
                "x": 0,
                "y": 20
              },
              "id": 9,
              "options": {
                "legend": {
                  "calcs": [
                    "sum"
                  ],
                  "displayMode": "table",
                  "placement": "right",
                  "showLegend": true
                },
                "tooltip": {
                  "mode": "multi",
                  "sort": "none"
                }
              },
              "targets": [
                {
                  "datasource": {
                    "type": "prometheus",
                    "uid": "prometheus"
                  },
                  "editorMode": "code",
                  "expr": "rate(velocitynavigator_routed_connections_total{server=~\\"^$server$\\"}[5m])",
                  "legendFormat": "{{server}}",
                  "range": true,
                  "refId": "A"
                }
              ],
              "title": "Routed Connections Rate (per second)",
              "type": "timeseries"
            },
            {
              "collapsed": false,
              "gridPos": {
                "h": 1,
                "w": 24,
                "x": 0,
                "y": 26
              },
              "id": 14,
              "title": "Traffic & Event Diagnostics",
              "type": "row"
            },
            {
              "datasource": {
                "type": "prometheus",
                "uid": "prometheus"
              },
              "fieldConfig": {
                "defaults": {
                  "custom": {
                    "drawStyle": "line",
                    "lineInterpolation": "smooth"
                  },
                  "unit": "pps"
                },
                "overrides": []
              },
              "gridPos": {
                "h": 8,
                "w": 12,
                "x": 0,
                "y": 27
              },
              "id": 10,
              "options": {
                "legend": {
                  "calcs": [
                    "mean",
                    "max"
                  ],
                  "displayMode": "table",
                  "placement": "bottom",
                  "showLegend": true
                },
                "tooltip": {
                  "mode": "multi",
                  "sort": "none"
                }
              },
              "targets": [
                {
                  "datasource": {
                    "type": "prometheus",
                    "uid": "prometheus"
                  },
                  "editorMode": "code",
                  "expr": "rate(velocitynavigator_player_joins_total[5m])",
                  "legendFormat": "Joins",
                  "range": true,
                  "refId": "A"
                },
                {
                  "datasource": {
                    "type": "prometheus",
                    "uid": "prometheus"
                  },
                  "editorMode": "code",
                  "expr": "rate(velocitynavigator_player_leaves_total[5m])",
                  "legendFormat": "Leaves",
                  "range": true,
                  "refId": "B"
                }
              ],
              "title": "Join/Leave Rates (per second)",
              "type": "timeseries"
            },
            {
              "datasource": {
                "type": "prometheus",
                "uid": "prometheus"
              },
              "fieldConfig": {
                "defaults": {
                  "color": {
                    "mode": "palette-classic"
                  },
                  "mappings": [],
                  "thresholds": {
                    "mode": "absolute",
                    "steps": [
                      {
                        "color": "green",
                        "value": null
                      }
                    ]
                  }
                },
                "overrides": []
              },
              "gridPos": {
                "h": 8,
                "w": 12,
                "x": 12,
                "y": 27
              },
              "id": 11,
              "options": {
                "displayMode": "basic",
                "orientation": "horizontal",
                "reduceOptions": {
                  "calcs": [
                    "lastNotNull"
                  ],
                  "fields": "",
                  "values": false
                },
                "showUnfilled": true
              },
              "targets": [
                {
                  "datasource": {
                    "type": "prometheus",
                    "uid": "prometheus"
                  },
                  "editorMode": "code",
                  "expr": "sum(velocitynavigator_redirects_total) by (reason)",
                  "legendFormat": "{{reason}}",
                  "range": true,
                  "refId": "A"
                }
              ],
              "title": "Redirect Reasons (Why Players Moved)",
              "type": "bargauge"
            },
            {
              "datasource": {
                "type": "prometheus",
                "uid": "prometheus"
              },
              "fieldConfig": {
                "defaults": {
                  "color": {
                    "mode": "thresholds"
                  },
                  "mappings": [],
                  "thresholds": {
                    "mode": "absolute",
                    "steps": [
                      {
                        "color": "green",
                        "value": null
                      },
                      {
                        "color": "orange",
                        "value": 1
                      },
                      {
                        "color": "red",
                        "value": 5
                      }
                    ]
                  }
                },
                "overrides": []
              },
              "gridPos": {
                "h": 8,
                "w": 12,
                "x": 0,
                "y": 35
              },
              "id": 12,
              "options": {
                "displayMode": "basic",
                "orientation": "horizontal",
                "reduceOptions": {
                  "calcs": [
                    "lastNotNull"
                  ],
                  "fields": "",
                  "values": false
                },
                "showUnfilled": true
              },
              "targets": [
                {
                  "datasource": {
                    "type": "prometheus",
                    "uid": "prometheus"
                  },
                  "editorMode": "code",
                  "expr": "velocitynavigator_circuit_breaker_trips_total{server=~\\"^$server$\\"}",
                  "legendFormat": "{{server}}",
                  "range": true,
                  "refId": "A"
                }
              ],
              "title": "Circuit Breaker Trips by Server",
              "type": "bargauge"
            },
            {
              "datasource": {
                "type": "prometheus",
                "uid": "prometheus"
              },
              "fieldConfig": {
                "defaults": {
                  "color": {
                    "mode": "palette-classic"
                  },
                  "mappings": [],
                  "thresholds": {
                    "mode": "absolute",
                    "steps": [
                      {
                        "color": "green",
                        "value": null
                      }
                    ]
                  }
                },
                "overrides": []
              },
              "gridPos": {
                "h": 8,
                "w": 12,
                "x": 12,
                "y": 35
              },
              "id": 13,
              "options": {
                "displayMode": "basic",
                "orientation": "horizontal",
                "reduceOptions": {
                  "calcs": [
                    "lastNotNull"
                  ],
                  "fields": "",
                  "values": false
                },
                "showUnfilled": true
              },
              "targets": [
                {
                  "datasource": {
                    "type": "prometheus",
                    "uid": "prometheus"
                  },
                  "editorMode": "code",
                  "expr": "velocitynavigator_fallback_events_total",
                  "legendFormat": "{{type}}",
                  "range": true,
                  "refId": "A"
                }
              ],
              "title": "Fallback Events by Type",
              "type": "bargauge"
            }
          ],
          "refresh": "5s",
          "schemaVersion": 38,
          "style": "dark",
          "tags": [
            "minecraft",
            "velocitynavigator",
            "loadbalancer"
          ],
          "templating": {
            "list": [
              {
                "current": {},
                "datasource": {
                  "type": "prometheus",
                  "uid": "prometheus"
                },
                "definition": "label_values(velocitynavigator_server_online, server)",
                "hide": 0,
                "includeAll": true,
                "multi": true,
                "name": "server",
                "options": [],
                "query": {
                  "query": "label_values(velocitynavigator_server_online, server)",
                  "refId": "StandardVariableQuery"
                },
                "refresh": 1,
                "regex": "",
                "skipUrlSync": false,
                "sort": 1,
                "type": "query"
              }
            ]
          },
          "time": {
            "from": "now-1h",
            "to": "now"
          },
          "timepicker": {},
          "timezone": "",
          "title": "VelocityNavigator Lobby Diagnostics",
          "uid": "vn_lobby_diagnostics",
          "version": 1
        }
        """;
    }
}
