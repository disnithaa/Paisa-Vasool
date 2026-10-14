import asyncio
from decimal import Decimal

import httpx

from app.main import app
from app.solver import OptimizationSolverError
from tests.factories import fixed_offer, percentage_offer, request_data


def send_request(method: str, path: str, payload: dict | None = None) -> httpx.Response:
    async def send() -> httpx.Response:
        transport = httpx.ASGITransport(app=app)
        async with httpx.AsyncClient(
            transport=transport,
            base_url="http://testserver",
        ) as client:
            return await client.request(method, path, json=payload)

    return asyncio.run(send())


def test_health_endpoint_reports_service_up() -> None:
    response = send_request("GET", "/health")

    assert response.status_code == 200
    assert response.json() == {"status": "UP"}


def test_optimize_returns_baseline_when_no_offers_are_supplied() -> None:
    response = send_request("POST", "/api/v1/optimize", request_data())

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "OPTIMAL"
    assert body["currency"] == "INR"
    assert body["base_amount"] == "250.00"
    assert body["selected_offer_id"] is None
    assert body["discount_amount"] == "0.00"
    assert body["final_payable_amount"] == "250.00"
    assert body["savings_amount"] == "0.00"
    assert body["solver"] == {
        "algorithm": "ortools-cp-sat",
        "solver_status": "OPTIMAL",
        "baseline_agrees": True,
    }


def test_optimize_returns_selected_offer_and_exact_amounts() -> None:
    response = send_request(
        "POST",
        "/api/v1/optimize",
        request_data(offers=[percentage_offer()]),
    )

    assert response.status_code == 200
    body = response.json()
    assert body["selected_offer_id"] == "00000000-0000-0000-0000-000000000001"
    assert body["discount_amount"] == "25.00"
    assert body["final_payable_amount"] == "225.00"
    assert body["savings_amount"] == "25.00"
    assert Decimal(body["final_payable_amount"]) + Decimal(body["savings_amount"]) == Decimal(
        body["base_amount"]
    )


def test_optimize_reports_ineligible_offer_reasons() -> None:
    response = send_request(
        "POST",
        "/api/v1/optimize",
        request_data(offers=[fixed_offer(active=False)]),
    )

    assert response.status_code == 200
    body = response.json()
    assert body["selected_offer_id"] is None
    assert body["rejected_offers"] == [
        {
            "offer_id": "00000000-0000-0000-0000-000000000002",
            "reason": "INACTIVE",
        }
    ]


def test_invalid_request_returns_422() -> None:
    response = send_request(
        "POST",
        "/api/v1/optimize",
        request_data(purchase_amount=250.0),
    )

    assert response.status_code == 422
    assert response.json()["detail"]


def test_solver_failure_returns_503(monkeypatch) -> None:
    def fail_solver(request):
        raise OptimizationSolverError("OR-Tools returned UNKNOWN during optimization")

    monkeypatch.setattr("app.main.solve_optimization", fail_solver)
    response = send_request("POST", "/api/v1/optimize", request_data())

    assert response.status_code == 503
    assert response.json() == {
        "detail": "OR-Tools returned UNKNOWN during optimization"
    }
