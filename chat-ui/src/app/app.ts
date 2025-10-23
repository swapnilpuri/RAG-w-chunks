import { Component, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Chat, Message } from './services/chat';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, CommonModule, FormsModule],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  protected readonly title = signal('Chat Interface');
  messages: Message[] = [];
  userInput: string = '';
  isLoading: boolean = false;

  constructor(private chatService: Chat) {}

  sendMessage(): void {
    if (!this.userInput.trim() || this.isLoading) {
      return;
    }

    const userMessage: Message = {
      role: 'user',
      content: this.userInput,
      timestamp: new Date()
    };

    this.messages.push(userMessage);
    const messageText = this.userInput;
    this.userInput = '';
    this.isLoading = true;

    this.chatService.sendMessage(messageText).subscribe({
      next: (response) => {
        const assistantMessage: Message = {
          role: 'assistant',
          content: response.response || response.message || 'No response',
          timestamp: new Date()
        };
        this.messages.push(assistantMessage);
        this.isLoading = false;
      },
      error: (error) => {
        console.error('Error sending message:', error);
        const errorMessage: Message = {
          role: 'assistant',
          content: `Sorry, there was an error processing your request. Please make sure the backend server is running. ${error.message || error.statusText || JSON.stringify(error)}`,
          timestamp: new Date()
        };
        this.messages.push(errorMessage);
        this.isLoading = false;
      }
    });
  }

  handleKeyPress(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.sendMessage();
    }
  }
}
