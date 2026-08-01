import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { BaseChartDirective } from 'ng2-charts';
import { Chart, ChartData, ChartOptions, registerables } from 'chart.js';
import { AnalyticsService } from '../../core/services/analytics.service';
import { RevenueDataPoint, CategoryRevenue } from '../../core/models/analytics.model';

Chart.register(...registerables);

@Component({
  selector: 'app-analytics',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    BaseChartDirective,
  ],
  template: `
    <div class="analytics-page container">
      <header class="analytics-page__header">
        <h1>Analytics</h1>
        <p class="analytics-page__subtitle">Revenue and category breakdown for the last 30 days.</p>
      </header>

      <div class="analytics-page__grid">
        <mat-card class="analytics-card">
          <mat-card-header>
            <mat-card-title>Revenue (Last 30 Days)</mat-card-title>
          </mat-card-header>
          <mat-card-content>
            @if (revenueLoading()) {
              <div class="analytics-card__skeleton">
                <div class="skeleton" style="height:300px;border-radius:12px;"></div>
              </div>
            } @else {
              <canvas
                baseChart
                [data]="revenueChartData()"
                [options]="lineChartOptions"
                type="line"
                role="img"
                aria-label="Line chart: revenue over the last 30 days"
                data-testid="revenue-chart"
              ></canvas>
            }
          </mat-card-content>
        </mat-card>

        <mat-card class="analytics-card">
          <mat-card-header>
            <mat-card-title>Top Categories</mat-card-title>
          </mat-card-header>
          <mat-card-content>
            @if (categoryLoading()) {
              <div class="analytics-card__skeleton">
                <div class="skeleton" style="height:300px;border-radius:12px;"></div>
              </div>
            } @else {
              <canvas
                baseChart
                [data]="categoryChartData()"
                [options]="barChartOptions"
                type="bar"
                role="img"
                aria-label="Bar chart: revenue by top product categories"
                data-testid="category-chart"
              ></canvas>
            }
          </mat-card-content>
        </mat-card>
      </div>
    </div>
  `,
  styleUrl: './analytics.page.scss',
})
export class AnalyticsPage implements OnInit {
  private readonly analytics = inject(AnalyticsService);

  readonly revenueLoading = signal(true);
  readonly categoryLoading = signal(true);

  readonly revenueChartData = signal<ChartData<'line'>>({
    labels: [],
    datasets: [{ data: [], label: 'Revenue ($)', borderColor: '#6366f1', backgroundColor: 'rgba(99,102,241,0.08)', fill: true, tension: 0.4 }],
  });

  readonly categoryChartData = signal<ChartData<'bar'>>({
    labels: [],
    datasets: [{ data: [], label: 'Revenue ($)', backgroundColor: ['#6366f1','#8b5cf6','#a78bfa','#c4b5fd','#ddd6fe','#ede9fe'] }],
  });

  readonly lineChartOptions: ChartOptions<'line'> = {
    responsive: true,
    maintainAspectRatio: true,
    plugins: {
      legend: { display: false },
      tooltip: { mode: 'index', intersect: false },
    },
    scales: {
      y: { beginAtZero: true, ticks: { callback: (v) => `$${v}` } },
    },
  };

  readonly barChartOptions: ChartOptions<'bar'> = {
    responsive: true,
    maintainAspectRatio: true,
    plugins: {
      legend: { display: false },
    },
    scales: {
      y: { beginAtZero: true, ticks: { callback: (v) => `$${v}` } },
    },
  };

  ngOnInit(): void {
    this.analytics.getRevenueStub(30).subscribe((data: RevenueDataPoint[]) => {
      this.revenueChartData.set({
        labels: data.map((d) => d.date),
        datasets: [{
          data: data.map((d) => d.revenue),
          label: 'Revenue ($)',
          borderColor: '#6366f1',
          backgroundColor: 'rgba(99,102,241,0.08)',
          fill: true,
          tension: 0.4,
        }],
      });
      this.revenueLoading.set(false);
    });

    this.analytics.getTopCategoriesStub().subscribe((data: CategoryRevenue[]) => {
      this.categoryChartData.set({
        labels: data.map((d) => d.category),
        datasets: [{
          data: data.map((d) => d.revenue),
          label: 'Revenue ($)',
          backgroundColor: ['#6366f1','#8b5cf6','#a78bfa','#c4b5fd','#ddd6fe','#ede9fe'],
        }],
      });
      this.categoryLoading.set(false);
    });
  }
}
